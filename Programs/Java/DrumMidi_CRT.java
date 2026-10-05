import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DrumMidi for Java 8 and Android ART.
 * Detects kick, snare, closed hi-hat, open hi-hat, ride, crash, rack tom, mid tom, and floor tom.
 * GM drum channel 10: kick 36, snare 38, closed hat 42, open hat 46, ride 51, crash 49,
 * rack tom 50, mid tom 47, floor tom 41.
 *
 * One file. WAV needs nothing else, so `javac DrumMidi.java` works on Android.
 * The jar also bundles JLayer 1.0.1 for MP3. A lone .java file tells you to use the jar for MP3.
 *
 *   java -jar DrumMidi.jar song.wav drums.mid [--sens 1.0] [--hat 1.0] [--tom 1.0] [--ride 1.0] [--crash 1.0] [--bpm 120] [--quantize 16] [--no-hpss] [--log [logfile]]
 *
 * --log writes the input and output names, the settings and the hits found to a text file
 * (DrumMidi_CRT_log.txt when no name is given). The settings also go into the MIDI itself as a text
 * event ("pulsekit-drummidi; ..."), so CompareHits can suggest changes to them.
 *
 * --sens sets kick and snare, and every other part that is not given its own value.
 * --hat, --tom, --ride and --crash replace --sens for that part (they do not multiply it).
 *
 * Bass guitar shares the kick and tom bands, so a low-band onset alone is not a drum.
 * A kick needs a beater click (2-6 kHz) with it, a tom needs a stick attack (1-4 kHz)
 * and a jump in raw low-mid level, and a snare needs its body (150-350 Hz) to jump
 * more than the kick band. Cutting the bass out with a filter would cut the kick too.
 */
public class DrumMidi_CRT{

    static final int FFT_SIZE = 2048;
    static final int HOP = 512;
    static final double MAX_HZ = 14000;
    static final double LOG_GAMMA = 100;
    static final int HPSS_KERNEL = 17;

    static final double KICK_LO = 40, KICK_HI = 130;
    static final double SNARE_BODY_LO = 150, SNARE_BODY_HI = 350;
    static final double SNARE_NOISE_LO = 2000, SNARE_NOISE_HI = 6000;
    static final double HAT_LO = 6000, HAT_HI = 14000;
    static final double RIDE_LO = 3000, RIDE_HI = 7000;
    static final double FLOOR_LO = 70, FLOOR_HI = 120;
    static final double MID_LO = 130, MID_HI = 190;
    static final double RACK_LO = 200, RACK_HI = 320;

    static final double ATTACK_LO = 1000, ATTACK_HI = 4000;
    static final double KICK_CLICK = 0.5;
    static final double SNARE_BODY_RISE = 0.6;
    static final double TOM_ATTACK_GATE = 0.5, TOM_ATTACK = 0.6, TOM_RISE = 0.5;
    static final double OPEN_HAT_TAIL = 0.45;

    static final int MIN_GAP_FRAMES = 5;
    static final double BASE_DELTA = 0.30;

    static final int PPQ = 480;
    static final int KICK_NOTE = 36, SNARE_NOTE = 38, CLOSED_HAT = 42, OPEN_HAT = 46, RIDE_NOTE = 51, CRASH_NOTE = 49;
    static final int FLOOR_TOM = 41, MID_TOM = 47, RACK_TOM = 50;

    static final double[] COS = new double[FFT_SIZE / 2];
    static final double[] SIN = new double[FFT_SIZE / 2];
    static {
        for (int i = 0; i < FFT_SIZE / 2; i++) {
            COS[i] = Math.cos(2 * Math.PI * i / FFT_SIZE);
            SIN[i] = -Math.sin(2 * Math.PI * i / FFT_SIZE);
        }
    }

    static class Audio {
        double[] samples;
        double sampleRate;
    }

    static class Hit {
        final double time;
        final int note;
        final double strength;
        Hit(double time, int note, double strength) {
            this.time = time;
            this.note = note;
            this.strength = strength;
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (Throwable ex) {
            String m = ex.getMessage();
            finish("Failed: " + (m == null || m.length() == 0 ? ex.toString() : m));
        }
    }

    static final String DEFAULT_LOG = "DrumMidi_CRT_log.txt";
    /** The --log file, or null; and its lines so far. */
    static String logPath;
    static final StringBuilder logText = new StringBuilder();

    /** Prints a line and keeps it for the --log file. */
    static void note(String line) {
        System.out.println(line);
        logText.append(line).append('\n');
    }

    /** Writes the --log file, then prints the last line (PyJav reads it as the run's status). */
    static void finish(String last) {
        logText.append(last).append('\n');
        if (logPath != null) {
            try {
                File f = new File(logPath);
                File parent = f.getAbsoluteFile().getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();
                FileOutputStream fos = new FileOutputStream(f);
                try {
                    fos.write(logText.toString().getBytes("UTF-8"));
                } finally {
                    fos.close();
                }
                System.out.println("Log: " + f.getAbsolutePath());
            } catch (Exception ex) {
                System.out.println("Could not write the log " + logPath + ": " + ex.getMessage());
            }
        }
        System.out.println(last);
    }

    static String num(double v) {
        String t = String.format(java.util.Locale.ROOT, "%.2f", v);
        while (t.endsWith("0")) t = t.substring(0, t.length() - 1);
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return t;
    }

    static void run(String[] args) throws Exception {
        List<String> rest = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i] == null ? "" : args[i].trim();
            if (a.length() == 0) continue;
            if ("--log".equals(a)) {
                // --log alone writes DrumMidi_CRT_log.txt.
                String nx = i + 1 < args.length && args[i + 1] != null ? args[i + 1].trim() : "";
                String low = nx.toLowerCase(java.util.Locale.ROOT);
                boolean named = nx.length() > 0 && !nx.startsWith("--") && !low.endsWith(".wav") && !low.endsWith(".wave")
                        && !low.endsWith(".mp3") && !low.endsWith(".mid") && !low.endsWith(".midi");
                logPath = named ? nx : DEFAULT_LOG;
                if (named) i++;
                continue;
            }
            rest.add(a);
        }
        args = rest.toArray(new String[0]);
        if (args.length < 2) {
            note("Usage: java DrumMidi <input.wav> <output.mid> "
                    + "[--sens N] [--hat N] [--tom N] [--ride N] [--crash N] [--bpm N] [--quantize N] [--no-hpss] [--log <logfile>]");
            finish("Failed: need an input wav and an output mid");
            return;
        }
        File inFile = new File(args[0]);
        File outFile = new File(args[1]);
        StringBuilder given = new StringBuilder();
        for (int i = 2; i < args.length; i++) given.append(given.length() == 0 ? "" : " ").append(args[i]);
        double sens = 1.0, hatSens = -1, tomSens = -1, rideSens = -1, crashSens = -1, bpmOverride = 0;
        int quant = 0;
        boolean hpss = true;
        for (int i = 2; i < args.length; i++) {
            String opt = args[i];
            if ("--sens".equals(opt)) sens = Double.parseDouble(args[++i]);
            else if ("--hat".equals(opt)) hatSens = Double.parseDouble(args[++i]);
            else if ("--tom".equals(opt)) tomSens = Double.parseDouble(args[++i]);
            else if ("--ride".equals(opt)) rideSens = Double.parseDouble(args[++i]);
            else if ("--crash".equals(opt)) crashSens = Double.parseDouble(args[++i]);
            else if ("--bpm".equals(opt)) bpmOverride = Double.parseDouble(args[++i]);
            else if ("--quantize".equals(opt)) quant = Integer.parseInt(args[++i]);
            else if ("--no-hpss".equals(opt)) hpss = false;
            else if (opt.startsWith("-")) throw new IllegalArgumentException("Unknown option: " + opt);
        }
        if (hatSens <= 0) hatSens = sens;
        if (tomSens <= 0) tomSens = sens;
        if (rideSens <= 0) rideSens = sens;
        if (crashSens <= 0) crashSens = sens;

        note("DrumMidi_CRT | input " + inFile.getName() + " | output " + outFile.getName());
        note("Settings: " + (given.length() == 0 ? "defaults" : given.toString()) + " | used: --sens " + num(sens)
                + " --hat " + num(hatSens) + " --tom " + num(tomSens) + " --ride " + num(rideSens) + " --crash " + num(crashSens)
                + (bpmOverride > 0 ? " --bpm " + num(bpmOverride) : "") + (quant > 0 ? " --quantize " + quant : "")
                + (hpss ? "" : " --no-hpss"));
        Audio audio = readAudio(inFile);
        double sr = audio.sampleRate;
        double fps = sr / HOP;
        note(String.format(java.util.Locale.ROOT, "Loaded %.1f s at %.0f Hz", audio.samples.length / sr, sr));
        if (audio.samples.length < FFT_SIZE * 4) {
            finish("Failed: audio is too short");
            return;
        }

        float[][] mag = spectrogram(audio.samples, sr);
        int maxBin = mag[0].length - 1;
        // Rises in the raw spectrum, before HPSS evens them out. A drum jumps; a bass note mostly does not.
        double[] lowRise = rise(mag, bin(40, sr, maxBin), bin(100, sr, maxBin));
        double[] bodyRise = rise(mag, bin(SNARE_BODY_LO, sr, maxBin), bin(SNARE_BODY_HI, sr, maxBin));
        double[] tomRise = rise(mag, bin(FLOOR_LO, sr, maxBin), bin(RACK_HI, sr, maxBin));
        double[] hatRaw = bandMean(mag, bin(HAT_LO, sr, maxBin), bin(HAT_HI, sr, maxBin));
        if (hpss) {
            System.out.println("Separating percussive content (a few seconds)...");
            applyHpss(mag);
        }

        double[] kick = flux(mag, bin(KICK_LO, sr, maxBin), bin(KICK_HI, sr, maxBin));
        double[] snareBody = flux(mag, bin(SNARE_BODY_LO, sr, maxBin), bin(SNARE_BODY_HI, sr, maxBin));
        double[] snareNoise = flux(mag, bin(SNARE_NOISE_LO, sr, maxBin), bin(SNARE_NOISE_HI, sr, maxBin));
        normalize(kick);
        normalize(snareBody);
        normalize(snareNoise);

        double[] snare = new double[kick.length];
        for (int t = 0; t < snare.length; t++) snare[t] = Math.sqrt(snareBody[t] * snareNoise[t]);
        normalize(snare);

        int hatLo = bin(HAT_LO, sr, maxBin);
        int hatHi = bin(HAT_HI, sr, maxBin);
        double[] hat = flux(mag, hatLo, hatHi);
        normalize(hat);

        int rideLo = bin(RIDE_LO, sr, maxBin);
        int rideHi = bin(RIDE_HI, sr, maxBin);
        double[] ride = flux(mag, rideLo, rideHi);
        double[] rideEnergy = bandMean(mag, rideLo, rideHi);
        normalize(ride);
        normalize(rideEnergy);

        int topLo = bin(8000, sr, maxBin);
        int topHi = bin(14000, sr, maxBin);
        double[] top = flux(mag, topLo, topHi);
        double[] topEnergy = bandMean(mag, topLo, topHi);
        normalize(top);
        normalize(topEnergy);
        double[] crash = new double[kick.length];
        for (int t = 0; t < crash.length; t++) {
            if (ride[t] < 0.35 || top[t] < 0.35) continue;
            crash[t] = Math.sqrt(ride[t] * top[t]);
        }

        // Kick onsets only count where the beater clicks. Bass notes have no click.
        double[] kickOnset = new double[kick.length];
        for (int t = 0; t < kick.length; t++) {
            double g = Math.min(1.0, near(snareNoise, t) / KICK_CLICK);
            kickOnset[t] = kick[t] * g * g;
        }
        normalize(kickOnset);
        // Toms likewise need a stick attack.
        double[] attack = flux(mag, bin(ATTACK_LO, sr, maxBin), bin(ATTACK_HI, sr, maxBin));
        normalize(attack);

        double[] floor = flux(mag, bin(FLOOR_LO, sr, maxBin), bin(FLOOR_HI, sr, maxBin));
        double[] mid = flux(mag, bin(MID_LO, sr, maxBin), bin(MID_HI, sr, maxBin));
        double[] rack = flux(mag, bin(RACK_LO, sr, maxBin), bin(RACK_HI, sr, maxBin));
        normalize(floor);
        normalize(mid);
        normalize(rack);
        double[] toms = new double[kick.length];
        for (int t = 0; t < toms.length; t++) {
            if (hat[t] > 0.75) continue;
            double g = Math.min(1.0, Math.max(attack[t], t > 0 ? attack[t - 1] : 0) / TOM_ATTACK_GATE);
            toms[t] = Math.max(floor[t], Math.max(mid[t], rack[t])) * g * g;
        }
        normalize(toms);

        double[] drums = new double[kick.length];
        for (int t = 0; t < drums.length; t++) drums[t] = kick[t] + snare[t];

        double delta = BASE_DELTA / sens;
        List<Hit> hits = new ArrayList<Hit>();
        // A snare's body jumps more than the kick band. Kick plus hat looks like a snare otherwise.
        List<Integer> snarePeaks = new ArrayList<Integer>();
        for (Integer p : pickPeaks(snare, delta)) {
            int t = p.intValue();
            double body = near(bodyRise, t);
            if (body < SNARE_BODY_RISE || body <= near(lowRise, t)) continue;
            snarePeaks.add(p);
            hits.add(new Hit(t * (double) HOP / sr, SNARE_NOTE, snare[t]));
        }
        boolean[] snareAt = new boolean[kick.length];
        for (Integer p : snarePeaks) mark(snareAt, p.intValue(), 1);
        for (Integer p : pickPeaks(kickOnset, delta)) {
            int t = p.intValue();
            if (snareAt[t] && kick[t] < 0.85 * near(snareBody, t)) continue;
            hits.add(new Hit(t * (double) HOP / sr, KICK_NOTE, kick[t]));
        }
        // Toms are not where a kick or snare would be found at full sensitivity,
        // so a lower --sens does not turn quiet kicks and snares into toms.
        boolean[] busy = new boolean[kick.length];
        for (Integer p : pickPeaks(snare, BASE_DELTA)) {
            int t = p.intValue();
            if (near(bodyRise, t) >= SNARE_BODY_RISE && near(bodyRise, t) > near(lowRise, t)) mark(busy, t, 2);
        }
        for (Integer p : pickPeaks(kickOnset, BASE_DELTA)) mark(busy, p.intValue(), 2);
        List<Integer> hatPeaks = pickPeaks(hat, (BASE_DELTA * 0.85) / Math.max(0.05, hatSens), 3);
        for (int i = 0; i < hatPeaks.size(); i++) {
            int t = hatPeaks.get(i).intValue();
            if (snare[t] > 1.0 && hat[t] < snare[t]) continue;
            if (isCrash(ride, top, rideEnergy, topEnergy, t)) continue;
            if (ride[t] >= hat[t] * 0.85 && rideRings(rideEnergy, t) && snareBody[t] < ride[t]) continue;
            boolean open = openHat(hatRaw, hatPeaks, i);
            hits.add(new Hit(t * (double) HOP / sr, open ? OPEN_HAT : CLOSED_HAT, hat[t]));
        }
        List<Integer> ridePeaks = pickPeaks(ride, (BASE_DELTA * 0.9) / Math.max(0.05, rideSens), 5);
        for (int i = 0; i < ridePeaks.size(); i++) {
            int t = ridePeaks.get(i).intValue();
            if (!rideRings(rideEnergy, t)) continue;
            if (isCrash(ride, top, rideEnergy, topEnergy, t)) continue;
            if (snareBody[t] > ride[t] && snareBody[t] > 0.8) continue;
            if (hat[t] > ride[t] * 1.35) continue;
            hits.add(new Hit(t * (double) HOP / sr, RIDE_NOTE, ride[t]));
        }
        List<Integer> crashPeaks = pickPeaks(crash, (BASE_DELTA * 1.7) / Math.max(0.05, crashSens), 32);
        for (int i = 0; i < crashPeaks.size(); i++) {
            int t = crashPeaks.get(i).intValue();
            if (!isCrash(ride, top, rideEnergy, topEnergy, t)) continue;
            if (!crashAttack(ride, top, t)) continue;
            hits.add(new Hit(t * (double) HOP / sr, CRASH_NOTE, crash[t]));
        }
        List<Integer> tomPeaks = pickPeaks(toms, BASE_DELTA / Math.max(0.05, tomSens), 4);
        for (int i = 0; i < tomPeaks.size(); i++) {
            int t = tomPeaks.get(i).intValue();
            if (busy[t]) continue;
            // A tom is struck as hard as a snare and its low-mid jumps. Bass notes fail one or both.
            if (near(attack, t) < TOM_ATTACK || near(tomRise, t) < TOM_RISE) continue;
            double f = floor[t], m = mid[t], r = rack[t];
            int note;
            double strength;
            if (f >= m && f >= r) {
                if (kick[t] > f * 1.15) continue;
                note = FLOOR_TOM;
                strength = f;
            } else if (m >= r) {
                note = MID_TOM;
                strength = m;
            } else {
                if (snare[t] > r) continue;
                note = RACK_TOM;
                strength = r;
            }
            hits.add(new Hit(t * (double) HOP / sr, note, strength));
        }
        hits.sort(Comparator.comparingDouble(new java.util.function.ToDoubleFunction<Hit>() {
            public double applyAsDouble(Hit h) { return h.time; }
        }));

        double lo, hi;
        if (bpmOverride > 0) {
            lo = hi = bpmOverride;
        } else {
            double coarse = estimateBpm(drums, fps);
            lo = coarse * 0.97;
            hi = coarse * 1.03;
        }
        double[] grid = fitBeatGrid(drums, fps, lo, hi);
        double bpm = grid[0], phaseSec = grid[1];
        note(String.format(java.util.Locale.ROOT, "Tempo: %.2f BPM, first beat near %.3f s", bpm, phaseSec));

        double tickPerSec = bpm / 60.0 * PPQ;
        double gridTicks = quant > 0 ? PPQ * 4.0 / quant : 0;
        double phaseTick = phaseSec * tickPerSec;

        List<MidiEv> events = new ArrayList<MidiEv>();
        int mpq = (int) Math.round(60000000.0 / bpm);
        events.add(new MidiEv(0, metaMsg(0x51, new byte[] {(byte) (mpq >> 16), (byte) (mpq >> 8), (byte) mpq})));
        events.add(new MidiEv(0, metaMsg(0x58, new byte[] {4, 2, 24, 8})));
        events.add(new MidiEv(0, metaMsg(0x03, "Drums (detected)".getBytes("UTF-8"))));
        // The settings travel with the MIDI, so CompareHits can suggest changes to them.
        String stamp = "pulsekit-drummidi; input=" + inFile.getName().replace(';', ',') + "; output=" + outFile.getName().replace(';', ',')
                + "; args=" + given.toString().replace(';', ',') + "; sens=" + num(sens) + "; hat=" + num(hatSens)
                + "; tom=" + num(tomSens) + "; ride=" + num(rideSens) + "; crash=" + num(crashSens)
                + "; bpm=" + (bpmOverride > 0 ? num(bpmOverride) : "auto") + "; quantize=" + (quant > 0 ? Integer.toString(quant) : "off")
                + "; hpss=" + (hpss ? "on" : "off") + "; tempo=" + num(bpm);
        events.add(new MidiEv(0, metaMsg(0x01, stamp.getBytes("UTF-8"))));

        Set<String> seen = new HashSet<String>();
        int kicks = 0, snares = 0, closed = 0, open = 0, rides = 0, crashes = 0, floors = 0, mids = 0, racks = 0;
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            long tick = Math.round(h.time * tickPerSec);
            if (gridTicks > 0) {
                long steps = Math.round((tick - phaseTick) / gridTicks);
                tick = Math.max(0, Math.round(phaseTick + steps * gridTicks));
            }
            if (!seen.add(h.note + ":" + tick)) continue;
            int vel = (int) Math.max(40, Math.min(127, 45 + 55 * h.strength));
            int hold = h.note == CRASH_NOTE ? 180 : (h.note == OPEN_HAT || h.note == RIDE_NOTE ? 120 : 24);
            events.add(new MidiEv(tick, shortMsg(0x90, h.note, vel)));
            events.add(new MidiEv(tick + hold, shortMsg(0x80, h.note, 0)));
            if (h.note == KICK_NOTE) kicks++;
            else if (h.note == SNARE_NOTE) snares++;
            else if (h.note == OPEN_HAT) open++;
            else if (h.note == CLOSED_HAT) closed++;
            else if (h.note == RIDE_NOTE) rides++;
            else if (h.note == CRASH_NOTE) crashes++;
            else if (h.note == FLOOR_TOM) floors++;
            else if (h.note == MID_TOM) mids++;
            else if (h.note == RACK_TOM) racks++;
        }
        File written = writeSmf(outFile, events);
        if (written == null || !written.isFile() || written.length() < 14) {
            finish("Failed: MIDI file was not written");
            return;
        }
        note(String.format(java.util.Locale.ROOT, "Wrote %s: %d kicks, %d snares, %d closed hats, %d open hats, %d rides, %d crashes, %d rack toms, %d mid toms, %d floor toms",
                written.getName(), kicks, snares, closed, open, rides, crashes, racks, mids, floors));
        note("MIDI Extracted to: " + written.getAbsolutePath());
        finish("Succeeded: " + written.getName());
    }

    static final class MidiEv {
        final long tick;
        final byte[] msg;
        MidiEv(long tick, byte[] msg) {
            this.tick = tick;
            this.msg = msg;
        }
    }

    static byte[] shortMsg(int command, int note, int vel) {
        return new byte[] {(byte) ((command & 0xF0) | 9), (byte) (note & 127), (byte) (vel & 127)};
    }

    static byte[] metaMsg(int type, byte[] data) {
        byte[] vlq = vlqBytes(data.length);
        byte[] msg = new byte[2 + vlq.length + data.length];
        msg[0] = (byte) 0xFF;
        msg[1] = (byte) type;
        System.arraycopy(vlq, 0, msg, 2, vlq.length);
        System.arraycopy(data, 0, msg, 2 + vlq.length, data.length);
        return msg;
    }

    static byte[] vlqBytes(int n) {
        byte[] buf = new byte[4];
        int i = 3;
        buf[i] = (byte) (n & 127);
        n >>= 7;
        while (n > 0 && i > 0) {
            i--;
            buf[i] = (byte) ((n & 127) | 128);
            n >>= 7;
        }
        byte[] out = new byte[4 - i];
        System.arraycopy(buf, i, out, 0, out.length);
        return out;
    }

    static void writeVlq(ByteArrayOutputStream out, long value) {
        long v = value < 0 ? 0 : value;
        byte[] buf = new byte[4];
        int i = 3;
        buf[i] = (byte) (v & 127);
        v >>= 7;
        while (v > 0 && i > 0) {
            i--;
            buf[i] = (byte) ((v & 127) | 128);
            v >>= 7;
        }
        out.write(buf, i, 4 - i);
    }

    /** Standard MIDI file, one track, channel 10. No midi.* library. */
    static File writeSmf(File outFile, List<MidiEv> events) throws Exception {
        Collections.sort(events, new Comparator<MidiEv>() {
            public int compare(MidiEv a, MidiEv b) {
                return a.tick < b.tick ? -1 : (a.tick > b.tick ? 1 : 0);
            }
        });
        ByteArrayOutputStream ev = new ByteArrayOutputStream();
        long last = 0;
        for (int i = 0; i < events.size(); i++) {
            MidiEv e = events.get(i);
            long tick = e.tick < last ? last : e.tick;
            writeVlq(ev, tick - last);
            last = tick;
            ev.write(e.msg);
        }
        writeVlq(ev, 0);
        ev.write(new byte[] {(byte) 0xFF, 0x2F, 0});
        byte[] data = ev.toByteArray();
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.write(new byte[] {
            'M', 'T', 'h', 'd', 0, 0, 0, 6,
            0, 0, 0, 1,
            (byte) ((PPQ >> 8) & 255), (byte) (PPQ & 255)
        });
        all.write(new byte[] {
            'M', 'T', 'r', 'k',
            (byte) ((data.length >> 24) & 255),
            (byte) ((data.length >> 16) & 255),
            (byte) ((data.length >> 8) & 255),
            (byte) (data.length & 255)
        });
        all.write(data);
        File dest = place(outFile);
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        FileOutputStream fos = new FileOutputStream(dest);
        try {
            fos.write(all.toByteArray());
        } finally {
            fos.close();
        }
        return dest;
    }

    static File place(File out) throws Exception {
        String path = out.getPath();
        boolean onRoot = path != null && path.startsWith("/") && path.indexOf('/', 1) < 0;
        if (out.isAbsolute() && !onRoot && !"/".equals(path)) return out;
        String dir = null;
        String[] keys = {"pulsekit.work", "user.dir", "java.io.tmpdir"};
        for (int i = 0; i < keys.length; i++) {
            String d = System.getProperty(keys[i]);
            if (d == null || d.length() < 2 || "/".equals(d) || ".".equals(d)) continue;
            File f = new File(d);
            if (f.isDirectory() && f.canWrite()) { dir = f.getAbsolutePath(); break; }
        }
        if (dir == null) throw new Exception(path + ": open failed: EROFS (Read-only file system)");
        String name = out.getName();
        if (name == null || name.length() == 0 || ".".equals(name) || "..".equals(name)) name = "out.mid";
        return new File(dir, name);
    }

    static File writtenFile(File outFile) {
        if (outFile.isFile() && outFile.length() > 0) return outFile;
        String[] keys = {"pulsekit.work", "user.dir", "java.io.tmpdir"};
        for (int i = 0; i < keys.length; i++) {
            String dir = System.getProperty(keys[i]);
            if (dir == null || dir.length() < 2) continue;
            File alt = new File(dir, outFile.getName());
            if (alt.isFile() && alt.length() > 0) return alt;
        }
        return outFile;
    }

    static Audio readAudio(File f) throws Exception {
        byte[] bytes = readFully(new FileInputStream(f));
        String name = f.getName().toLowerCase();
        if (name.endsWith(".mp3") || isId3OrFrame(bytes)) return readMp3(bytes);
        return readWav(bytes);
    }

    static boolean isId3OrFrame(byte[] b) {
        if (b.length >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') return true;
        return b.length >= 2 && b[0] == (byte) 0xFF && (b[1] & 0xE0) == 0xE0;
    }

    static byte[] readFully(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    static void installJl(Class<?> utils, Class<?> hookType) throws Exception {
        if (utils.getResourceAsStream("/javazoom/jl/decoder/sfd.ser") != null) return;
        Object hook = Proxy.newProxyInstance(hookType.getClassLoader(), new Class<?>[] {hookType}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("getResourceAsStream".equals(method.getName()) && args != null && args.length == 1) {
                    Class<?> tables = Class.forName("JlTables");
                    byte[] body = (byte[]) tables.getMethod("bytes", String.class).invoke(null, args[0]);
                    return body == null ? null : new ByteArrayInputStream(body);
                }
                return null;
            }
        });
        utils.getMethod("setHook", hookType).invoke(null, hook);
    }

    static Audio readMp3(byte[] d) throws Exception {
        Class<?> bitC;
        Class<?> decC;
        try {
            bitC = Class.forName("javazoom.jl.decoder.Bitstream");
            decC = Class.forName("javazoom.jl.decoder.Decoder");
            Class<?> utils = Class.forName("javazoom.jl.decoder.JavaLayerUtils");
            Class<?> hook = Class.forName("javazoom.jl.decoder.JavaLayerHook");
            installJl(utils, hook);
        } catch (ClassNotFoundException missing) {
            throw new Exception("MP3 needs DrumMidi_CRT.jar (PyJav's Java menu). This single file reads WAV.");
        }
        Object bs = bitC.getConstructor(InputStream.class).newInstance(new ByteArrayInputStream(d));
        Object dec = decC.newInstance();
        double[] mono = new double[44100];
        int frames = 0;
        double sr = 44100;
        try {
            Method readFrame = bitC.getMethod("readFrame");
            Method closeFrame = bitC.getMethod("closeFrame");
            Method frequency = Class.forName("javazoom.jl.decoder.Header").getMethod("frequency");
            Method decodeFrame = decC.getMethod("decodeFrame", Class.forName("javazoom.jl.decoder.Header"), bitC);
            while (true) {
                Object h = readFrame.invoke(bs);
                if (h == null) break;
                sr = ((Number) frequency.invoke(h)).doubleValue();
                Object buf = decodeFrame.invoke(dec, h, bs);
                int nch = Math.max(1, ((Number) buf.getClass().getMethod("getChannelCount").invoke(buf)).intValue());
                short[] s = (short[]) buf.getClass().getMethod("getBuffer").invoke(buf);
                int n = ((Number) buf.getClass().getMethod("getBufferLength").invoke(buf)).intValue();
                int got = n / nch;
                if (frames + got > mono.length) {
                    double[] grow = new double[Math.max(frames + got, mono.length * 2)];
                    System.arraycopy(mono, 0, grow, 0, frames);
                    mono = grow;
                }
                for (int i = 0; i < got; i++) {
                    double sum = 0;
                    for (int c = 0; c < nch; c++) sum += s[i * nch + c];
                    mono[frames + i] = sum / (nch * 32768.0);
                }
                frames += got;
                closeFrame.invoke(bs);
            }
        } finally {
            try { bitC.getMethod("close").invoke(bs); } catch (Exception ignored) {}
        }
        if (frames < (int) (sr / 2)) throw new Exception("Could not decode that MP3");
        double[] out = new double[frames];
        System.arraycopy(mono, 0, out, 0, frames);
        Audio a = new Audio();
        a.samples = out;
        a.sampleRate = sr;
        return a;
    }

    static Audio readWav(byte[] b) throws Exception {
        if (b.length < 12 || b[0] != 'R' || b[1] != 'I' || b[2] != 'F' || b[3] != 'F') {
            throw new Exception("Could not read audio data");
        }
        int channels = 1, bits = 16, rate = 44100, format = 1;
        byte[] data = null;
        int pos = 12;
        while (pos + 8 <= b.length) {
            String id = new String(b, pos, 4, "ISO-8859-1");
            int size = u32(b, pos + 4);
            int start = pos + 8;
            if (size < 0 || start > b.length) break;
            if (start + size > b.length) size = b.length - start;
            if ("fmt ".equals(id) && size >= 16) {
                format = u16(b, start);
                channels = u16(b, start + 2);
                rate = u32(b, start + 4);
                bits = u16(b, start + 14);
            } else if ("data".equals(id)) {
                data = new byte[size];
                System.arraycopy(b, start, data, 0, size);
            }
            pos = start + size + (size & 1);
        }
        if (data == null || channels < 1 || rate < 1000) throw new Exception("WAV has no PCM data");
        int bytesPer = Math.max(1, bits / 8);
        int frames = data.length / (bytesPer * channels);
        double[] mono = new double[frames];
        for (int i = 0; i < frames; i++) {
            double s = 0;
            for (int c = 0; c < channels; c++) {
                int o = (i * channels + c) * bytesPer;
                s += sample(data, o, format, bits);
            }
            mono[i] = s / channels;
        }
        Audio a = new Audio();
        a.samples = mono;
        a.sampleRate = rate;
        return a;
    }

    static double sample(byte[] d, int o, int format, int bits) {
        if (format == 3 && bits == 32 && o + 4 <= d.length) {
            int bits32 = (d[o] & 255) | ((d[o + 1] & 255) << 8) | ((d[o + 2] & 255) << 16) | (d[o + 3] << 24);
            return Float.intBitsToFloat(bits32);
        }
        if (bits == 8) return ((d[o] & 255) - 128) / 128.0;
        if (bits == 24 && o + 3 <= d.length) {
            int v = (d[o] & 255) | ((d[o + 1] & 255) << 8) | (d[o + 2] << 16);
            return v / 8388608.0;
        }
        if (o + 2 > d.length) return 0;
        int v = (short) ((d[o] & 255) | (d[o + 1] << 8));
        return v / 32768.0;
    }

    static int u16(byte[] b, int i) {
        return (b[i] & 255) | ((b[i + 1] & 255) << 8);
    }

    static int u32(byte[] b, int i) {
        return (b[i] & 255) | ((b[i + 1] & 255) << 8) | ((b[i + 2] & 255) << 16) | ((b[i + 3] & 255) << 24);
    }

    static float[][] spectrogram(double[] x, double sr) {
        int maxBin = Math.min(FFT_SIZE / 2, (int) Math.round(MAX_HZ * FFT_SIZE / sr));
        int nFrames = x.length / HOP + 1;
        float[][] mag = new float[nFrames][maxBin + 1];
        double[] win = new double[FFT_SIZE];
        for (int i = 0; i < FFT_SIZE; i++) win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / FFT_SIZE);
        double[] re = new double[FFT_SIZE], im = new double[FFT_SIZE];
        for (int t = 0; t < nFrames; t++) {
            int start = t * HOP - FFT_SIZE / 2;
            for (int j = 0; j < FFT_SIZE; j++) {
                int idx = start + j;
                re[j] = (idx >= 0 && idx < x.length) ? x[idx] * win[j] : 0;
                im[j] = 0;
            }
            fft(re, im);
            for (int k = 0; k <= maxBin; k++) mag[t][k] = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
        }
        return mag;
    }

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            int half = len / 2, step = n / len;
            for (int i = 0; i < n; i += len) {
                for (int k = 0; k < half; k++) {
                    double wr = COS[k * step], wi = SIN[k * step];
                    int a = i + k, b = a + half;
                    double tr = re[b] * wr - im[b] * wi;
                    double ti = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                }
            }
        }
    }

    static void applyHpss(float[][] mag) {
        int T = mag.length, F = mag[0].length, h = HPSS_KERNEL / 2;
        float[][] out = new float[T][F];
        float[] buf = new float[HPSS_KERNEL];
        for (int t = 0; t < T; t++) {
            for (int f = 0; f < F; f++) {
                for (int i = -h; i <= h; i++) buf[i + h] = mag[t][clamp(f + i, 0, F - 1)];
                Arrays.sort(buf);
                float p = buf[h];
                for (int i = -h; i <= h; i++) buf[i + h] = mag[clamp(t + i, 0, T - 1)][f];
                Arrays.sort(buf);
                float hm = buf[h];
                float p2 = p * p, h2 = hm * hm;
                out[t][f] = mag[t][f] * p2 / (p2 + h2 + 1e-12f);
            }
        }
        for (int t = 0; t < T; t++) mag[t] = out[t];
    }

    static int bin(double hz, double sr, int maxBin) {
        return Math.max(0, Math.min(maxBin, (int) Math.round(hz * FFT_SIZE / sr)));
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double[] flux(float[][] mag, int lo, int hi) {
        double[] o = new double[mag.length];
        for (int t = 1; t < mag.length; t++) {
            double s = 0;
            for (int k = lo; k <= hi; k++) {
                double d = Math.log1p(LOG_GAMMA * mag[t][k]) - Math.log1p(LOG_GAMMA * mag[t - 1][k]);
                if (d > 0) s += d;
            }
            o[t] = s / (hi - lo + 1);
        }
        return o;
    }

    /** Mean magnitude in a band. Used to see whether a hi-hat rings (open) or dies (closed). */
    static double[] bandMean(float[][] mag, int lo, int hi) {
        double[] o = new double[mag.length];
        int n = Math.max(1, hi - lo + 1);
        for (int t = 0; t < mag.length; t++) {
            double s = 0;
            for (int k = lo; k <= hi; k++) s += mag[t][k];
            o[t] = s / n;
        }
        return o;
    }

    /**
     * Open hats ring in the top band. A hat that is hit again within ~90 ms is closed,
     * because an open hat is left to sound. The ring is measured in the raw band level
     * (HPSS removes it) above the level just before the hit, so cymbal wash does not count.
     */
    static boolean openHat(double[] energy, List<Integer> peaks, int index) {
        int t = peaks.get(index).intValue();
        if (index + 1 < peaks.size()) {
            int next = peaks.get(index + 1).intValue();
            if (next - t < 8) return false;
        }
        int n = energy.length;
        double pre = Math.min(energy[Math.max(0, t - 3)], energy[Math.max(0, t - 2)]);
        double peak = Math.max(energy[t], energy[Math.min(n - 1, t + 1)]) - pre;
        if (peak < 1e-6) return false;
        int a = Math.min(n - 1, t + 5);
        int b = Math.min(n - 1, t + 9);
        double tail = 0;
        int count = 0;
        for (int i = a; i <= b; i++) {
            tail += energy[i];
            count++;
        }
        tail /= Math.max(1, count);
        return tail - pre > peak * OPEN_HAT_TAIL;
    }

    /** A ride keeps ringing in the mid-high band well after an open hat would have died. */
    static boolean rideRings(double[] energy, int t) {
        double now = energy[t];
        if (now < 1e-6) return false;
        int a = Math.min(energy.length - 1, t + 14);
        int b = Math.min(energy.length - 1, t + 32);
        if (b <= a) return false;
        double tail = 0;
        int count = 0;
        for (int i = a; i <= b; i++) {
            tail += energy[i];
            count++;
        }
        tail /= Math.max(1, count);
        return tail > now * 0.22;
    }

    /** A crash hits the ride band and the top band together, and both keep washing. */
    static boolean isCrash(double[] ride, double[] top, double[] rideEnergy, double[] topEnergy, int t) {
        double r = ride[t], h = top[t];
        if (r < 0.58 || h < 0.58) return false;
        double hi = Math.max(r, h), lo = Math.min(r, h);
        if (lo < hi * 0.62) return false;
        return crashWash(rideEnergy, t) && crashWash(topEnergy, t);
    }

    /** The crash has to jump in. A cymbal that is already ringing is not a new hit. */
    static boolean crashAttack(double[] ride, double[] top, int t) {
        if (t < 4) return true;
        double prev = 0;
        for (int i = t - 4; i < t; i++) prev += ride[i] + top[i];
        prev /= 8.0;
        double now = (ride[t] + top[t]) / 2.0;
        return now > prev * 1.45;
    }

    /** Wash still loud from about 180 ms to 500 ms. Hats and snares die before that. */
    static boolean crashWash(double[] energy, int t) {
        double now = energy[t];
        if (now < 1e-6) return false;
        int a = Math.min(energy.length - 1, t + 16);
        int b = Math.min(energy.length - 1, t + 42);
        if (b <= a) return false;
        double tail = 0;
        int count = 0;
        for (int i = a; i <= b; i++) {
            tail += energy[i];
            count++;
        }
        tail /= Math.max(1, count);
        return tail > now * 0.30;
    }

    /** Mean log rise over two frames in a band of the raw spectrum. */
    static double[] rise(float[][] mag, int lo, int hi) {
        double[] o = new double[mag.length];
        for (int t = 2; t < mag.length; t++) {
            double s = 0;
            for (int k = lo; k <= hi; k++) {
                s += Math.log1p(LOG_GAMMA * mag[t][k]) - Math.log1p(LOG_GAMMA * mag[t - 2][k]);
            }
            o[t] = s / (hi - lo + 1);
        }
        return o;
    }

    /** Largest value within one frame of t. Onsets in different bands can land a frame apart. */
    static double near(double[] o, int t) {
        double v = o[t];
        if (t > 0) v = Math.max(v, o[t - 1]);
        if (t + 1 < o.length) v = Math.max(v, o[t + 1]);
        return v;
    }

    static void mark(boolean[] at, int t, int r) {
        for (int i = Math.max(0, t - r); i <= Math.min(at.length - 1, t + r); i++) at[i] = true;
    }

    static void normalize(double[] o) {
        double[] s = o.clone();
        Arrays.sort(s);
        double p = s[(int) (0.98 * (s.length - 1))];
        if (p < 1e-9) p = 1e-9;
        for (int i = 0; i < o.length; i++) o[i] /= p;
    }

    static List<Integer> pickPeaks(double[] o, double delta) {
        return pickPeaks(o, delta, MIN_GAP_FRAMES);
    }

    static List<Integer> pickPeaks(double[] o, double delta, int minGap) {
        final int W = 3, MEAN_W = 16;
        List<Integer> peaks = new ArrayList<Integer>();
        int n = o.length, last = -1000;
        for (int t = 1; t < n - 1; t++) {
            double v = o[t];
            if (v < delta) continue;
            boolean isMax = true;
            for (int i = Math.max(0, t - W); i <= Math.min(n - 1, t + W); i++) {
                if (o[i] > v) { isMax = false; break; }
            }
            if (!isMax) continue;
            int a = Math.max(0, t - MEAN_W), b = Math.min(n - 1, t + MEAN_W);
            double mean = 0;
            for (int i = a; i <= b; i++) mean += o[i];
            mean /= (b - a + 1);
            if (v <= mean + delta) continue;
            if (!peaks.isEmpty() && t - last < minGap) {
                if (v > o[last]) {
                    peaks.set(peaks.size() - 1, Integer.valueOf(t));
                    last = t;
                }
                continue;
            }
            peaks.add(Integer.valueOf(t));
            last = t;
        }
        return peaks;
    }

    static double estimateBpm(double[] env, double fps) {
        int n = env.length;
        int minLag = Math.max(2, (int) Math.floor(fps * 60 / 180.0));
        int maxLag = (int) Math.ceil(fps * 60 / 60.0);
        if (maxLag + 2 >= n) return 120;
        double mean = 0;
        for (int i = 0; i < env.length; i++) mean += env[i];
        mean /= n;
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = env[i] - mean;

        double[] ac = new double[maxLag + 2];
        for (int lag = minLag - 1; lag <= maxLag + 1; lag++) {
            double s = 0;
            for (int i = 0; i + lag < n; i++) s += x[i] * x[i + lag];
            ac[lag] = s / (n - lag);
        }
        int bestLag = minLag;
        double best = -Double.MAX_VALUE;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double bpm = 60 * fps / lag;
            double oct = Math.log(bpm / 120.0) / Math.log(2);
            double score = ac[lag] * Math.exp(-0.5 * oct * oct / (0.8 * 0.8));
            if (score > best) { best = score; bestLag = lag; }
        }
        double a = ac[bestLag - 1], b = ac[bestLag], c = ac[bestLag + 1];
        double denom = a - 2 * b + c;
        double lagRef = bestLag + (denom != 0 ? clampD(0.5 * (a - c) / denom, -1, 1) : 0);
        return 60 * fps / lagRef;
    }

    static double[] fitBeatGrid(double[] env, double fps, double lo, double hi) {
        double bestScore = -Double.MAX_VALUE, bestBpm = lo, bestPhase = 0;
        for (double bpm = lo; bpm <= hi + 1e-9; bpm += 0.02) {
            double period = 60.0 * fps / bpm;
            for (double ph = 0; ph < period; ph += 0.5) {
                double s = 0;
                int count = 0;
                for (double pos = ph; pos < env.length - 1; pos += period) {
                    int i = (int) pos;
                    double fr = pos - i;
                    s += env[i] * (1 - fr) + env[i + 1] * fr;
                    count++;
                }
                s /= Math.max(count, 1);
                if (s > bestScore) { bestScore = s; bestBpm = bpm; bestPhase = ph; }
            }
        }
        return new double[] {bestBpm, bestPhase / fps};
    }

    static double clampD(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}