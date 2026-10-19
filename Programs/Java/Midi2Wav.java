import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Usage: java Midi2Wav <drums.mid> [output.wav] [--sf2 soundfont.sf2] [--rate N] [--volume N] [--addtodb] [--test] [--dir <folder>] [--recursive] [--kits <folder>]
 *
 * Plays a drum MIDI file with a SoundFont and saves what it sounds like as a WAV file (16-bit stereo).
 *
 *   drums.mid   the MIDI file (format 0 or 1). Its drum channel (10) is played; a file with no
 *               notes there is played as drums whole.
 *   output.wav  the WAV made; <drums>2wav.wav when it is not given.
 *   --sf2       the SoundFont (.sf2): its drum kit (bank 128), else its first preset. In Pulsekit,
 *               PyJav gives Pulsekit's own kit (the active drum set, as File > Export SF2 writes it)
 *               when none is chosen. A drum note the SoundFont has no sound for is played with the
 *               nearest General MIDI drum it has (an open hat for a pedal hat...), else left out.
 *   --rate N    sample rate: 22050, 44100 (default) or 48000.
 *   --volume N  percent, 1 to 400 (100). A mix that would clip is turned down to fit.
 *   --addtodb   Pulsekit also keeps the run in the prompt library (Programs > Midi2Wav): the MIDI and
 *               the SoundFont as reference files, the WAV as its result file.
 *   --test      Pulsekit then tests its drum import on the WAV: DrumMidi_CRT makes a MIDI of the
 *               hits it hears (imported as a file set and a song), and CompareHits compares the WAV,
 *               that MIDI and the song (with suggestions for DrumMidi's switches), and the MIDI
 *               played against DrumMidi's (what it missed or added).
 *   --dir       plays every MIDI file in the folder instead (--recursive: its subfolders too), with
 *               every kit: --sf2 and each SoundFont in --kits <folder>. Each WAV is
 *               <midi>-<kit>.wav beside its MIDI, as vst_render.py --dir names them. With --test,
 *               Pulsekit then runs DrumMidi_CRT --dir on the WAVs and CompareHits --batch on the
 *               folder (a MidiDrumGen --count or MidiBatch folder: its batch.txt lists the grooves).
 *
 * Plain Java (no lambdas, java.* only), so PyJav can compile it on a phone.
 */
public class Midi2Wav {
    private Midi2Wav() {}

    public static void main(String[] args) {
        int code;
        try {
            code = run(args);
        } catch (Throwable ex) {
            String m = ex.getMessage();
            System.out.println("Failed: " + (m == null || m.length() == 0 ? ex.toString() : m));
            code = 1;
        }
        // In Pulsekit on the phone the program runs inside the app: never exit there.
        if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
    }

    static void usage() {
        System.out.println("Usage: java Midi2Wav <drums.mid> [output.wav] [--sf2 soundfont.sf2] [--rate N] [--volume N] [--addtodb] [--test] [--dir <folder>] [--recursive] [--kits <folder>]");
    }

    static int run(String[] args) throws Exception {
        String input = null;
        String output = null;
        String sf2 = null;
        String dir = null;
        String kits = null;
        boolean recursive = false;
        int rate = 44100;
        double volume = 100;
        for (int i = 0; i < args.length; i++) {
            String a = args[i] == null ? "" : args[i].trim();
            if (a.length() == 0) continue;
            if (a.equals("--sf2") && i + 1 < args.length) sf2 = args[++i].trim();
            else if (a.equals("--dir") && i + 1 < args.length) dir = args[++i].trim();
            else if (a.equals("--kits") && i + 1 < args.length) kits = args[++i].trim();
            else if (a.equals("--recursive")) recursive = true;
            else if (a.equals("--rate") && i + 1 < args.length) rate = (int) Math.round(number(args[++i], a));
            else if (a.equals("--volume") && i + 1 < args.length) volume = number(args[++i], a);
            else if (a.equals("--addtodb") || a.equals("--test")) {
                // Pulsekit keeps the files, or tests its drum import on the WAV, after the run.
            } else if (a.equals("-h") || a.equals("--help")) {
                usage();
                return 0;
            } else if (a.startsWith("--")) {
                System.out.println("Failed: unknown argument " + a);
                usage();
                return 2;
            } else if (input == null) input = a;
            else if (output == null) output = a;
            else {
                System.out.println("Failed: one MIDI file and one WAV file name");
                usage();
                return 2;
            }
        }
        if (dir != null && dir.length() > 0) return runDir(new File(dir), recursive, sf2, kits, rate, volume);
        if (input == null) {
            System.out.println("Failed: give the drum MIDI file to play");
            usage();
            return 2;
        }
        if (rate != 22050 && rate != 44100 && rate != 48000) {
            System.out.println("Failed: --rate is 22050, 44100 or 48000");
            return 2;
        }
        if (volume < 1 || volume > 400) {
            System.out.println("Failed: --volume is 1 to 400 (percent)");
            return 2;
        }
        File in = new File(input);
        if (!in.isFile()) {
            System.out.println("Failed: no MIDI file " + input);
            return 1;
        }
        if (sf2 == null || sf2.length() == 0) {
            System.out.println("Failed: give the SoundFont with --sf2 (in Pulsekit, PyJav gives Pulsekit's kit when none is chosen)");
            return 2;
        }
        File font = new File(sf2);
        if (!font.isFile()) {
            System.out.println("Failed: no SoundFont " + sf2);
            return 1;
        }
        if (output == null || output.length() == 0) output = in.getName().replaceAll("(?i)\\.midi?$", "") + "2wav.wav";
        if (!output.toLowerCase(Locale.ROOT).endsWith(".wav")) output = output + ".wav";
        File out = inWork(output);

        SoundFont sf = new SoundFont(font);
        try {
            return play(in, sf, font, out, rate, volume, true) ? 0 : 1;
        } finally {
            sf.close();
        }
    }

    /**
     * --dir: every MIDI file in the folder (not DrumMidi's own *-dm.mid) played with every kit, each
     * as <midi>-<kit>.wav beside it. A SoundFont is read once for all the files.
     */
    static int runDir(File dir, boolean recursive, String sf2, String kits, int rate, double volume) throws Exception {
        if (!dir.isDirectory() && !isPls(dir)) {
            System.out.println("Failed: no folder " + dir.getPath());
            return 1;
        }
        List<File> fonts = new ArrayList<File>();
        if (sf2 != null && sf2.length() > 0) fonts.add(new File(sf2));
        if (kits != null && kits.length() > 0) {
            File[] in = new File(kits).listFiles();
            if (in == null) {
                System.out.println("Failed: no folder " + kits);
                return 1;
            }
            Arrays.sort(in);
            for (File f : in) if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".sf2") && !fonts.contains(f)) fonts.add(f);
        }
        if (fonts.isEmpty()) {
            System.out.println("Failed: give the SoundFont with --sf2, or a folder of them with --kits (in Pulsekit, PyJav gives Pulsekit's kit when none is chosen)");
            return 2;
        }
        for (File f : fonts) {
            if (!f.isFile()) {
                System.out.println("Failed: no SoundFont " + f.getPath());
                return 1;
            }
        }
        List<File> midis = new ArrayList<File>();
        midisIn(dir, recursive, midis);
        if (midis.isEmpty()) {
            System.out.println("Failed: no MIDI files in " + dir.getName());
            return 1;
        }
        System.out.println("Midi2Wav: " + midis.size() + " MIDI file" + (midis.size() == 1 ? "" : "s") + " in " + dir.getName() + " with " + fonts.size() + " kit" + (fonts.size() == 1 ? "" : "s"));
        int done = 0;
        for (File font : fonts) {
            SoundFont sf = new SoundFont(font);
            try {
                String kit = font.getName().replaceAll("(?i)\\.sf2$", "").replaceAll("[^A-Za-z0-9._ -]+", "_");
                for (File m : midis) {
                    if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Aborted");
                    File out = new File(m.getParentFile(), m.getName().replaceAll("(?i)\\.midi?$", "") + "-" + kit + ".wav");
                    try {
                        if (play(m, sf, font, out, rate, volume, false)) done++;
                    } catch (IOException ex) {
                        System.out.println("Failed " + m.getName() + ": " + ex.getMessage());
                    }
                }
            } finally {
                sf.close();
            }
        }
        System.out.println("Succeeded: " + done + " of " + midis.size() * fonts.size() + " WAV files in " + dir.getName());
        return done > 0 ? 0 : 1;
    }

    static void midisIn(File dir, boolean recursive, List<File> into) {
        File[] in = listed(dir);
        if (in == null) return;
        if (!isPls(dir)) Arrays.sort(in);
        for (File f : in) {
            String low = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (low.endsWith(".mid") || low.endsWith(".midi")) && !low.endsWith("-dm.mid")) into.add(f);
        }
        if (recursive) for (File f : in) if (f.isDirectory() && !f.getName().startsWith(".")) midisIn(f, true, into);
    }

    /** Plays one MIDI file with a SoundFont into `out` (`one`: the run's only file); false (said why) when it has no notes. */
    static boolean play(File in, SoundFont sf, File font, File out, int rate, double volume, boolean one) throws IOException {
        Song song = readMidi(readAll(in));
        System.out.println("MIDI: " + in.getName() + ", " + song.notes.size() + " drum " + (song.notes.size() == 1 ? "note" : "notes")
            + (song.allChannels ? " (no notes on the drum channel: all channels played)" : "") + ", " + secs(song.seconds) + " s"
            + (song.bpm > 0 ? " at " + Math.round(song.bpm) + " BPM" : ""));
        if (song.notes.isEmpty()) {
            System.out.println((one ? "Failed: " : "Left out ") + in.getName() + " has no notes to play");
            return false;
        }
        System.out.println("SoundFont: " + font.getName() + (sf.name.length() > 0 ? " (" + sf.name + ")" : "") + ", preset " + sf.presetName
            + (sf.bank == 128 ? " (drum kit)" : " (bank " + sf.bank + ", no drum kit in it)"));
        float[][] mix = render(song, sf, rate);
        double peak = 0;
        double g = volume / 100.0;
        for (int c = 0; c < 2; c++) for (float v : mix[c]) peak = Math.max(peak, Math.abs(v * g));
        if (peak > 0.98) {
            g = g * 0.98 / peak;
            System.out.println("Note: turned down to " + Math.round(g * 100) + "% so the mix does not clip");
        } else if (peak < 0.01) {
            // Velocity 1 is about 80 dB down: a MIDI with very soft notes is played as it is.
            System.out.println(String.format(Locale.ROOT, "Note: the mix is very quiet (%.0f dB below full scale): the MIDI's notes are soft "
                + "(low velocities); --volume raises it", 20 * Math.log10(Math.max(1e-9, peak))));
        }
        if (sf.missing.size() > 0) {
            StringBuilder sb = new StringBuilder("Note: no sound in the SoundFont for ");
            int k = 0;
            for (Map.Entry<Integer, Integer> e : sf.missing.entrySet()) {
                sb.append(k++ > 0 ? ", " : "").append(drumName(e.getKey())).append(" (").append(e.getKey()).append(", ").append(e.getValue()).append("x)");
            }
            System.out.println(sb);
        }
        if (sf.substituted.size() > 0) {
            StringBuilder sb = new StringBuilder("Note: played with the nearest drum: ");
            int k = 0;
            for (Map.Entry<Integer, Integer> e : sf.substituted.entrySet()) {
                sb.append(k++ > 0 ? ", " : "").append(drumName(e.getKey())).append(" as ").append(drumName(e.getValue()));
            }
            System.out.println(sb);
        }
        byte[] wav = wav(mix, rate, g);
        FileOutputStream fos = new FileOutputStream(out);
        try {
            fos.write(wav);
        } finally {
            fos.close();
        }
        System.out.println("Wrote " + out.getName() + " (" + size(wav.length) + ", " + secs(mix[0].length / (double) rate) + " s, " + rate + " Hz stereo)");
        if (one) System.out.println("Succeeded: " + out.getName());
        return true;
    }

    // ------------------------------------------------------------------ MIDI

    static final class Note {
        double start;
        double end;
        int key;
        int velocity;
    }

    static final class Song {
        final List<Note> notes = new ArrayList<Note>();
        double seconds;
        double bpm;
        boolean allChannels;
    }

    /** The notes of a standard MIDI file, in seconds (tempo changes followed); the drum channel's, else all. */
    static Song readMidi(byte[] d) throws IOException {
        if (d.length < 14 || !four(d, 0).equals("MThd")) throw new IOException("not a MIDI file (no MThd)");
        int hlen = be32(d, 4);
        int format = be16(d, 8);
        int tracks = be16(d, 10);
        int division = be16(d, 12);
        if ((division & 0x8000) != 0) throw new IOException("SMPTE-timed MIDI files are not supported");
        if (division == 0) throw new IOException("the MIDI file's division is 0");
        if (format > 1) System.out.println("Note: format 2 MIDI file: its tracks are played together");
        // Every event as {tick, order, kind, channel, key, velocity}: kind 0 tempo (key = microseconds per beat), 1 note on, 2 note off.
        List<long[]> events = new ArrayList<long[]>();
        int at = 8 + hlen;
        long order = 0;
        for (int t = 0; t < tracks && at + 8 <= d.length; t++) {
            if (!four(d, at).equals("MTrk")) {
                at += 8 + Math.max(0, be32(d, at + 4));
                t--;
                if (at >= d.length) break;
                continue;
            }
            int len = be32(d, at + 4);
            int p = at + 8;
            int end = Math.min(d.length, p + len);
            long tick = 0;
            int status = 0;
            while (p < end) {
                long[] dv = vlq(d, p);
                tick += dv[0];
                p = (int) dv[1];
                if (p >= end) break;
                int b = d[p] & 0xff;
                if (b == 0xff) {
                    if (p + 2 > end) break;
                    int type = d[p + 1] & 0xff;
                    long[] lv = vlq(d, p + 2);
                    int mlen = (int) lv[0];
                    int mp = (int) lv[1];
                    if (type == 0x51 && mlen >= 3 && mp + 3 <= end) {
                        long us = ((d[mp] & 0xffL) << 16) | ((d[mp + 1] & 0xffL) << 8) | (d[mp + 2] & 0xffL);
                        events.add(new long[] {tick, order++, 0, 0, us, 0});
                    }
                    p = mp + mlen;
                    continue;
                }
                if (b == 0xf0 || b == 0xf7) {
                    long[] lv = vlq(d, p + 1);
                    p = (int) (lv[1] + lv[0]);
                    continue;
                }
                int s;
                if ((b & 0x80) != 0) {
                    s = b;
                    p++;
                    if (b < 0xf0) status = b;
                } else {
                    s = status;
                }
                if (s == 0) {
                    p++;
                    continue;
                }
                int kind = s & 0xf0;
                int ch = s & 0x0f;
                int data = kind == 0xc0 || kind == 0xd0 ? 1 : 2;
                if (p + data > end) break;
                if (kind == 0x90 || kind == 0x80) {
                    int key = d[p] & 0x7f;
                    int vel = d[p + 1] & 0x7f;
                    boolean on = kind == 0x90 && vel > 0;
                    events.add(new long[] {tick, order++, on ? 1 : 2, ch, key, vel});
                }
                p += data;
            }
            at = end;
        }
        Collections.sort(events, new Comparator<long[]>() {
            @Override
            public int compare(long[] a, long[] b) {
                if (a[0] != b[0]) return a[0] < b[0] ? -1 : 1;
                // At the same tick: tempo first, then note offs, then note ons.
                int ka = a[2] == 0 ? 0 : a[2] == 2 ? 1 : 2;
                int kb = b[2] == 0 ? 0 : b[2] == 2 ? 1 : 2;
                if (ka != kb) return ka - kb;
                return a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0;
            }
        });
        boolean drums = false;
        for (long[] e : events) if (e[2] == 1 && e[3] == 9) drums = true;
        Song song = new Song();
        song.allChannels = !drums;
        double us = 500000;
        double firstTempo = -1;
        long lastTick = 0;
        double now = 0;
        Map<Integer, Note> open = new HashMap<Integer, Note>();
        for (long[] e : events) {
            now += (e[0] - lastTick) * us / 1e6 / division;
            lastTick = e[0];
            if (e[2] == 0) {
                if (e[4] > 0) us = e[4];
                if (firstTempo < 0) firstTempo = us;
                continue;
            }
            if (drums && e[3] != 9) continue;
            int id = (int) (e[3] * 128 + e[4]);
            if (e[2] == 1) {
                Note n = new Note();
                n.start = now;
                n.end = now + 0.25;
                n.key = (int) e[4];
                n.velocity = (int) e[5];
                Note was = open.put(Integer.valueOf(id), n);
                if (was != null) was.end = now;
                song.notes.add(n);
            } else {
                Note n = open.remove(Integer.valueOf(id));
                if (n != null) n.end = Math.max(n.start + 0.005, now);
            }
            song.seconds = Math.max(song.seconds, now);
        }
        song.bpm = 60e6 / (firstTempo > 0 ? firstTempo : 500000);
        for (Note n : song.notes) song.seconds = Math.max(song.seconds, n.end);
        return song;
    }

    // ------------------------------------------------------------- SoundFont

    /** One sound a key plays: a sample, where it starts and ends, how it is pitched, panned and looped. */
    static final class Zone {
        int keyLo = 0;
        int keyHi = 127;
        int velLo = 0;
        int velHi = 127;
        int sample = -1;
        long start;
        long end;
        long loopStart;
        long loopEnd;
        int rootKey = -1;
        double tune;
        double scale = 100;
        double attenuation;
        double pan;
        int modes;
        int exclusive;
        double release = 0.06;
        int sampleRate;
        /** The volume envelope: delay, attack, hold and decay (seconds), and the sustain level (dB down). */
        double delay;
        double attack;
        double hold;
        double decay;
        double sustainDb;

        /** The envelope's level `t` seconds into the note (before its release). */
        double level(double t) {
            if (t < delay) return 0;
            t -= delay;
            if (t < attack) return t / attack;
            t -= attack;
            if (t < hold) return 1;
            t -= hold;
            if (sustainDb <= 0) return 1;
            if (t < decay) return Math.pow(10, -sustainDb * (t / decay) / 20.0);
            return Math.pow(10, -sustainDb / 20.0);
        }
    }

    static final class SoundFont {
        final RandomAccessFile file;
        long smplAt;
        long smplLen;
        String name = "";
        String presetName = "";
        int bank;
        final List<Zone> zones = new ArrayList<Zone>();
        final Map<Integer, short[]> samples = new HashMap<Integer, short[]>();
        /** Drum notes left out (no sound), with how many times; and those played as another drum. */
        final Map<Integer, Integer> missing = new TreeMap<Integer, Integer>();
        final Map<Integer, Integer> substituted = new TreeMap<Integer, Integer>();
        // shdr: name, start, end, loop start, loop end, rate, pitch, correction, type.
        long[][] headers;

        SoundFont(File f) throws IOException {
            file = new RandomAccessFile(f, "r");
            try {
                read();
            } catch (IOException ex) {
                file.close();
                throw ex;
            } catch (RuntimeException ex) {
                file.close();
                throw new IOException(f.getName() + " could not be read as a SoundFont (" + ex + ")");
            }
        }

        void close() {
            try {
                file.close();
            } catch (IOException ignored) {
                // read already
            }
        }

        private byte[] bytes(long at, int len) throws IOException {
            byte[] b = new byte[len];
            file.seek(at);
            file.readFully(b);
            return b;
        }

        private void read() throws IOException {
            byte[] head = bytes(0, 12);
            if (!four(head, 0).equals("RIFF") || !four(head, 8).equals("sfbk")) throw new IOException("not a SoundFont (.sf2) file");
            long end = Math.min(file.length(), 8 + (u32(head, 4) & 0xffffffffL));
            Map<String, byte[]> pdta = new HashMap<String, byte[]>();
            long p = 12;
            while (p + 8 <= end) {
                byte[] h = bytes(p, 12 <= end - p ? 12 : 8);
                String id = four(h, 0);
                long len = u32(h, 4) & 0xffffffffL;
                if (id.equals("LIST") && h.length >= 12) {
                    String type = four(h, 8);
                    long q = p + 12;
                    long qend = Math.min(end, p + 8 + len);
                    while (q + 8 <= qend) {
                        byte[] sh = bytes(q, 8);
                        String sid = four(sh, 0);
                        long slen = u32(sh, 4) & 0xffffffffL;
                        if (type.equals("sdta") && sid.equals("smpl")) {
                            smplAt = q + 8;
                            smplLen = slen;
                        } else if (type.equals("pdta") && slen < 64L * 1024 * 1024) {
                            pdta.put(sid, bytes(q + 8, (int) Math.min(slen, qend - q - 8)));
                        } else if (type.equals("INFO") && sid.equals("INAM") && slen < 256) {
                            name = cstr(bytes(q + 8, (int) slen), 0, (int) slen);
                        }
                        q += 8 + slen + (slen & 1);
                    }
                }
                p += 8 + len + (len & 1);
            }
            if (smplLen == 0) throw new IOException("the SoundFont has no samples");
            for (String need : new String[] {"phdr", "pbag", "pgen", "inst", "ibag", "igen", "shdr"}) {
                if (!pdta.containsKey(need)) throw new IOException("the SoundFont has no " + need + " list");
            }
            byte[] shdr = pdta.get("shdr");
            int ns = shdr.length / 46;
            headers = new long[ns][];
            for (int i = 0; i < ns; i++) {
                int b = i * 46;
                headers[i] = new long[] {u32(shdr, b + 20) & 0xffffffffL, u32(shdr, b + 24) & 0xffffffffL, u32(shdr, b + 28) & 0xffffffffL,
                    u32(shdr, b + 32) & 0xffffffffL, u32(shdr, b + 36) & 0xffffffffL, shdr[b + 40] & 0xff, shdr[b + 41], u16(shdr, b + 44)};
            }
            // The preset: the drum kit (bank 128, preset 0 first), else the first by bank and number.
            byte[] phdr = pdta.get("phdr");
            int np = phdr.length / 38 - 1;
            if (np < 1) throw new IOException("the SoundFont has no presets");
            int best = -1;
            long bestKey = Long.MAX_VALUE;
            for (int i = 0; i < np; i++) {
                int b = i * 38;
                int preset = u16(phdr, b + 20);
                int bk = u16(phdr, b + 22);
                long key = (bk == 128 ? 0 : 1L << 20) + (long) bk * 256 + preset;
                if (key < bestKey) {
                    bestKey = key;
                    best = i;
                }
            }
            presetName = cstr(phdr, best * 38, 20);
            bank = u16(phdr, best * 38 + 22);
            int bag0 = u16(phdr, best * 38 + 24);
            int bag1 = u16(phdr, (best + 1) * 38 + 24);
            byte[] pbag = pdta.get("pbag");
            byte[] pgen = pdta.get("pgen");
            byte[] inst = pdta.get("inst");
            byte[] ibag = pdta.get("ibag");
            byte[] igen = pdta.get("igen");
            int[] presetGlobal = null;
            for (int z = bag0; z < bag1; z++) {
                int[] gens = gens(pbag, pgen, z);
                if (gens[41] < 0) {
                    if (z == bag0) presetGlobal = gens;
                    continue;
                }
                int[] pz = merge(presetGlobal, gens);
                int instrument = pz[41];
                if ((instrument + 1) * 22 + 22 > inst.length) continue;
                int ib0 = u16(inst, instrument * 22 + 20);
                int ib1 = u16(inst, (instrument + 1) * 22 + 20);
                int[] instGlobal = null;
                for (int iz = ib0; iz < ib1; iz++) {
                    int[] ig = gens(ibag, igen, iz);
                    if (ig[53] < 0) {
                        if (iz == ib0) instGlobal = ig;
                        continue;
                    }
                    zone(merge(instGlobal, ig), pz);
                }
            }
            if (zones.isEmpty()) throw new IOException("the SoundFont's preset " + presetName + " has no sounds");
        }

        /** A zone's generators: amount by operator (-1 for none given; ranges as lo | hi << 8). */
        private int[] gens(byte[] bag, byte[] gen, int z) {
            int[] out = new int[64];
            java.util.Arrays.fill(out, Integer.MIN_VALUE);
            out[41] = -1;
            out[53] = -1;
            if ((z + 1) * 4 + 2 > bag.length) return out;
            int g0 = u16(bag, z * 4);
            int g1 = u16(bag, (z + 1) * 4);
            for (int g = g0; g < g1 && g * 4 + 4 <= gen.length; g++) {
                int op = u16(gen, g * 4);
                if (op >= 64) continue;
                int amt = op == 43 || op == 44 ? u16(gen, g * 4 + 2) : (short) u16(gen, g * 4 + 2);
                if (op == 41 || op == 53) amt = u16(gen, g * 4 + 2);
                out[op] = amt;
            }
            return out;
        }

        private int[] merge(int[] global, int[] local) {
            if (global == null) return local;
            int[] out = local.clone();
            for (int i = 0; i < 64; i++) {
                if (i == 41 || i == 53) continue;
                if (out[i] == Integer.MIN_VALUE) out[i] = global[i];
            }
            return out;
        }

        /** Timecents as seconds (-12000 or less: none), at most `max`. */
        private static double seconds(int tc, double max) {
            if (tc <= -12000) return 0;
            return Math.min(max, Math.pow(2, tc / 1200.0));
        }

        private static int val(int[] g, int op, int dflt) {
            return g[op] == Integer.MIN_VALUE ? dflt : g[op];
        }

        /** An instrument zone (`g`) under a preset zone (`p`): its sample and settings, the preset's added. */
        private void zone(int[] g, int[] p) {
            int sid = g[53];
            if (sid < 0 || sid >= headers.length - 1) return;
            long[] h = headers[sid];
            if ((h[7] & 0x8000) != 0) return;
            Zone z = new Zone();
            int ik = val(g, 43, 0x7f00);
            int pk = val(p, 43, 0x7f00);
            z.keyLo = Math.max(ik & 0xff, pk & 0xff);
            z.keyHi = Math.min((ik >> 8) & 0xff, (pk >> 8) & 0xff);
            int iv = val(g, 44, 0x7f00);
            int pv = val(p, 44, 0x7f00);
            z.velLo = Math.max(iv & 0xff, pv & 0xff);
            z.velHi = Math.min((iv >> 8) & 0xff, (pv >> 8) & 0xff);
            if (z.keyHi < z.keyLo || z.velHi < z.velLo) return;
            z.sample = sid;
            z.start = h[0] + val(g, 0, 0) + 32768L * val(g, 4, 0);
            z.end = h[1] + val(g, 1, 0) + 32768L * val(g, 12, 0);
            z.loopStart = h[2] + val(g, 2, 0) + 32768L * val(g, 45, 0);
            z.loopEnd = h[3] + val(g, 3, 0) + 32768L * val(g, 50, 0);
            int over = val(g, 58, -1);
            z.rootKey = over >= 0 && over <= 127 ? over : (h[5] <= 127 ? (int) h[5] : 60);
            z.tune = val(g, 51, 0) + val(p, 51, 0) + (val(g, 52, 0) + val(p, 52, 0) + h[6]) / 100.0;
            z.scale = val(g, 56, 100);
            z.attenuation = Math.max(0, val(g, 48, 0) + val(p, 48, 0)) / 10.0;
            z.pan = Math.max(-500, Math.min(500, val(g, 17, 0) + val(p, 17, 0))) / 500.0;
            z.modes = val(g, 54, 0) & 3;
            z.exclusive = val(g, 57, 0);
            int rel = val(g, 38, -12000) + val(p, 38, 0);
            z.release = Math.max(0.02, Math.min(3.0, Math.pow(2, rel / 1200.0)));
            z.delay = seconds(val(g, 33, -12000) + val(p, 33, 0), 20);
            z.attack = seconds(val(g, 34, -12000) + val(p, 34, 0), 20);
            z.hold = seconds(val(g, 35, -12000) + val(p, 35, 0), 20);
            z.decay = Math.max(0.001, seconds(val(g, 36, -12000) + val(p, 36, 0), 40));
            z.sustainDb = Math.max(0, Math.min(1440, val(g, 37, 0) + val(p, 37, 0))) / 10.0;
            z.sampleRate = (int) Math.max(1000, Math.min(192000, h[4]));
            zones.add(z);
        }

        /** The zones a key plays at that velocity. */
        List<Zone> zonesFor(int key, int vel) {
            List<Zone> out = new ArrayList<Zone>();
            for (Zone z : zones) if (key >= z.keyLo && key <= z.keyHi && vel >= z.velLo && vel <= z.velHi) out.add(z);
            if (out.isEmpty()) {
                // A velocity outside every zone: the zones of the key at any velocity.
                for (Zone z : zones) if (key >= z.keyLo && key <= z.keyHi) out.add(z);
            }
            return out;
        }

        /** The sample's frames (16-bit), read once. */
        short[] frames(Zone z) throws IOException {
            Integer id = Integer.valueOf(z.sample);
            short[] s = samples.get(id);
            if (s != null) return s;
            long[] h = headers[z.sample];
            long from = Math.max(0, Math.min(h[0], z.start));
            long to = Math.min(smplLen / 2, Math.max(h[1], z.end));
            int n = (int) Math.max(0, Math.min(to - from, 60L * 192000));
            byte[] b = bytes(smplAt + from * 2, n * 2);
            s = new short[n];
            for (int i = 0; i < n; i++) s[i] = (short) ((b[i * 2] & 0xff) | (b[i * 2 + 1] << 8));
            samples.put(id, s);
            // Offsets are kept from the sample's own start.
            sampleBase.put(id, Long.valueOf(from));
            return s;
        }

        final Map<Integer, Long> sampleBase = new HashMap<Integer, Long>();
    }

    /** General MIDI drums a missing one can be played as, nearest first. */
    static final int[][] ALIASES = {
        {35, 36}, {36, 35}, {37, 38, 40}, {38, 40, 37}, {39, 38, 40}, {40, 38, 37},
        {41, 43, 45}, {43, 41, 45}, {45, 47, 43}, {47, 45, 48}, {48, 50, 47}, {50, 48, 47},
        {42, 44, 46}, {44, 42, 46}, {46, 42, 44}, {49, 57, 52, 55, 51}, {57, 49, 52, 55, 51},
        {52, 49, 57, 55}, {55, 49, 57, 52}, {51, 59, 53, 49}, {59, 51, 53}, {53, 51, 59},
        {54, 42, 46}, {56, 37}, {58, 37}, {60, 62, 37}, {61, 63, 37}, {62, 60}, {63, 61}, {64, 63},
    };

    // ---------------------------------------------------------------- render

    static float[][] render(Song song, SoundFont sf, int rate) throws IOException {
        // How long each note sounds: to its sample's end (one-shot), or to note off plus its release (looped).
        List<Object[]> voices = new ArrayList<Object[]>();
        double total = song.seconds;
        Map<Integer, Integer> playedAs = new HashMap<Integer, Integer>();
        for (Note n : song.notes) {
            int key = n.key;
            List<Zone> zs = sf.zonesFor(key, n.velocity);
            if (zs.isEmpty()) {
                Integer known = playedAs.get(Integer.valueOf(key));
                if (known != null) {
                    key = known.intValue();
                    zs = sf.zonesFor(key, n.velocity);
                } else {
                    for (int[] row : ALIASES) {
                        if (row[0] != n.key) continue;
                        for (int k = 1; k < row.length && zs.isEmpty(); k++) {
                            zs = sf.zonesFor(row[k], n.velocity);
                            if (!zs.isEmpty()) key = row[k];
                        }
                    }
                    if (!zs.isEmpty()) {
                        playedAs.put(Integer.valueOf(n.key), Integer.valueOf(key));
                        sf.substituted.put(Integer.valueOf(n.key), Integer.valueOf(key));
                    }
                }
            }
            if (zs.isEmpty()) {
                Integer c = sf.missing.get(Integer.valueOf(n.key));
                sf.missing.put(Integer.valueOf(n.key), Integer.valueOf(c == null ? 1 : c.intValue() + 1));
                continue;
            }
            for (Zone z : zs) {
                short[] frames = sf.frames(z);
                if (frames.length < 2) continue;
                double step = Math.pow(2, ((key - z.rootKey) * z.scale / 100.0 + z.tune) / 12.0) * z.sampleRate / rate;
                long base = sf.sampleBase.get(Integer.valueOf(z.sample)).longValue();
                double from = Math.max(0, z.start - base);
                double to = Math.min(frames.length, z.end - base);
                boolean loop = (z.modes == 1 || z.modes == 3) && z.loopEnd - base > z.loopStart - base + 4;
                double len = loop ? n.end - n.start + z.release : (to - from) / step / rate;
                // A sound whose envelope dies away (sustain 100 dB or more down) ends when it has.
                if (z.sustainDb >= 100) len = Math.min(len, z.delay + z.attack + z.hold + z.decay);
                voices.add(new Object[] {n, z, frames, Double.valueOf(step), Double.valueOf(from), Double.valueOf(to), Boolean.valueOf(loop), Integer.valueOf(key), Double.valueOf(len), Long.valueOf(base)});
                total = Math.max(total, n.start + len);
            }
        }
        // Exclusive classes (a closed hat cuts the open one): a sound stops when the next of its class starts.
        Map<Integer, List<Object[]>> byClass = new HashMap<Integer, List<Object[]>>();
        for (Object[] v : voices) {
            int cls = ((Zone) v[1]).exclusive;
            if (cls == 0) continue;
            List<Object[]> l = byClass.get(Integer.valueOf(cls));
            if (l == null) byClass.put(Integer.valueOf(cls), l = new ArrayList<Object[]>());
            l.add(v);
        }
        Map<Object[], Double> cut = new HashMap<Object[], Double>();
        for (List<Object[]> l : byClass.values()) {
            for (Object[] v : l) {
                double s = ((Note) v[0]).start;
                double next = Double.MAX_VALUE;
                for (Object[] w : l) {
                    double t = ((Note) w[0]).start;
                    if (t > s && t < next) next = t;
                }
                if (next < Double.MAX_VALUE) cut.put(v, Double.valueOf(next));
            }
        }
        int frames = (int) Math.min(Integer.MAX_VALUE - 8, Math.ceil((total + 0.05) * rate));
        if (frames > rate * 60L * 30) throw new IOException("the MIDI file is over 30 minutes long");
        float[][] mix = new float[2][frames];
        for (Object[] v : voices) {
            Note n = (Note) v[0];
            Zone z = (Zone) v[1];
            short[] smp = (short[]) v[2];
            double step = ((Double) v[3]).doubleValue();
            double from = ((Double) v[4]).doubleValue();
            double to = ((Double) v[5]).doubleValue();
            boolean loop = ((Boolean) v[6]).booleanValue();
            double len = ((Double) v[8]).doubleValue();
            long base = ((Long) v[9]).longValue();
            double ls = 0;
            double le = 0;
            if (loop) {
                ls = Math.max(from, z.loopStart - base);
                le = Math.min(smp.length - 1, z.loopEnd - base);
            }
            Double stop = cut.get(v);
            double stopAt = stop == null ? Double.MAX_VALUE : stop.doubleValue();
            double vel = n.velocity / 127.0;
            double gain = vel * vel * Math.pow(10, -z.attenuation * 0.4 / 20.0);
            double left = gain * Math.cos((z.pan + 1) * Math.PI / 4);
            double right = gain * Math.sin((z.pan + 1) * Math.PI / 4);
            int start = (int) Math.round(n.start * rate);
            int count = (int) Math.min(frames - start, Math.ceil(len * rate));
            int offAt = (int) Math.round((n.end - n.start) * rate);
            int releaseFrames = (int) Math.max(1, z.release * rate);
            int cutAt = stopAt == Double.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round((stopAt - n.start) * rate);
            int fade = Math.max(1, rate / 100);
            double pos = from;
            for (int i = 0; i < count && start + i < frames; i++) {
                if (cutAt != Integer.MAX_VALUE && i >= cutAt + fade) break;
                if (loop) {
                    if (pos >= le) pos = ls + (pos - le) % Math.max(1, le - ls);
                } else if (pos >= to - 1) break;
                int ip = (int) pos;
                double fr = pos - ip;
                double s = smp[ip] + (ip + 1 < smp.length ? (smp[ip + 1] - smp[ip]) * fr : 0);
                s /= 32768.0;
                double t = i / (double) rate;
                double env = z.level(loop && i > offAt ? offAt / (double) rate : t);
                if (i < 32 && z.attack == 0) env *= i / 32.0;
                // The release falls 100 dB over its time (linear in dB, as SoundFont synths do).
                if (loop && i > offAt) env *= Math.pow(10, -5.0 * (i - offAt) / releaseFrames);
                if (i > cutAt) env *= Math.max(0, 1 - (i - cutAt) / (double) fade);
                mix[0][start + i] += (float) (s * env * left);
                mix[1][start + i] += (float) (s * env * right);
                pos += step;
            }
        }
        return mix;
    }

    // ------------------------------------------------------------------- WAV

    static byte[] wav(float[][] mix, int rate, double gain) {
        int n = mix[0].length;
        byte[] out = new byte[44 + n * 4];
        putStr(out, 0, "RIFF");
        put32(out, 4, 36 + n * 4);
        putStr(out, 8, "WAVE");
        putStr(out, 12, "fmt ");
        put32(out, 16, 16);
        put16(out, 20, 1);
        put16(out, 22, 2);
        put32(out, 24, rate);
        put32(out, 28, rate * 4);
        put16(out, 32, 4);
        put16(out, 34, 16);
        putStr(out, 36, "data");
        put32(out, 40, n * 4);
        int o = 44;
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < 2; c++) {
                int v = (int) Math.round(mix[c][i] * gain * 32767);
                v = Math.max(-32768, Math.min(32767, v));
                out[o++] = (byte) v;
                out[o++] = (byte) (v >> 8);
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- utils

    /** The output in the work folder: on the phone PyJav's (pulsekit.work), where Pulsekit keeps it; an absolute path stays. */
    static File inWork(String name) {
        File f = new File(name);
        if (f.isAbsolute()) return f;
        String work = System.getProperty("pulsekit.work");
        String dir = work != null && work.length() > 0 ? work : System.getProperty("user.dir", ".");
        return new File(dir, name);
    }

    static byte[] readAll(File f) throws IOException {
        if (f.length() > 64L * 1024 * 1024) throw new IOException(f.getName() + " is too large for a MIDI file");
        byte[] b = new byte[(int) f.length()];
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            r.readFully(b);
        } finally {
            r.close();
        }
        return b;
    }

    static double number(String s, String what) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(what + " needs a number, not " + s);
        }
    }

    static String drumName(int key) {
        String[] names = {"Acoustic bass drum", "Kick", "Side stick", "Snare", "Clap", "Electric snare", "Low floor tom", "Closed hat",
            "High floor tom", "Pedal hat", "Low tom", "Open hat", "Low-mid tom", "High-mid tom", "Crash", "High tom", "Ride", "China",
            "Ride bell", "Tambourine", "Splash", "Cowbell", "Crash 2", "Vibraslap", "Ride 2", "Hi bongo", "Low bongo", "Mute hi conga",
            "Open hi conga", "Low conga"};
        return key >= 35 && key - 35 < names.length ? names[key - 35] : "note " + key;
    }

    static String secs(double s) {
        return String.format(Locale.ROOT, "%.1f", s);
    }

    static String size(long bytes) {
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static long[] vlq(byte[] d, int p) {
        long v = 0;
        for (int i = 0; i < 4 && p < d.length; i++) {
            int b = d[p++] & 0xff;
            v = (v << 7) | (b & 0x7f);
            if ((b & 0x80) == 0) break;
        }
        return new long[] {v, p};
    }

    static String four(byte[] d, int at) {
        if (at + 4 > d.length) return "";
        return new String(d, at, 4, StandardCharsets.US_ASCII);
    }

    static String cstr(byte[] d, int at, int len) {
        int n = 0;
        while (n < len && at + n < d.length && d[at + n] != 0) n++;
        return new String(d, at, n, StandardCharsets.US_ASCII).trim();
    }

    static int u16(byte[] d, int at) {
        return (d[at] & 0xff) | ((d[at + 1] & 0xff) << 8);
    }

    /** Little endian (RIFF: SoundFont, WAV). */
    static int u32(byte[] d, int at) {
        return (d[at] & 0xff) | ((d[at + 1] & 0xff) << 8) | ((d[at + 2] & 0xff) << 16) | ((d[at + 3] & 0xff) << 24);
    }

    /** Big endian (MIDI). */
    static int be32(byte[] d, int at) {
        return ((d[at] & 0xff) << 24) | ((d[at + 1] & 0xff) << 16) | ((d[at + 2] & 0xff) << 8) | (d[at + 3] & 0xff);
    }

    static int be16(byte[] d, int at) {
        return ((d[at] & 0xff) << 8) | (d[at + 1] & 0xff);
    }

    static void putStr(byte[] d, int at, String s) {
        for (int i = 0; i < s.length(); i++) d[at + i] = (byte) s.charAt(i);
    }

    static void put16(byte[] d, int at, int v) {
        d[at] = (byte) v;
        d[at + 1] = (byte) (v >> 8);
    }

    static void put32(byte[] d, int at, int v) {
        d[at] = (byte) v;
        d[at + 1] = (byte) (v >> 8);
        d[at + 2] = (byte) (v >> 16);
        d[at + 3] = (byte) (v >> 24);
    }

    /** A playlist (.pls) given instead of a folder (or list): its files are the ones used. */
    static boolean isPls(File f) {
        return f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pls") && f.isFile();
    }

    /** The folder's files; for a playlist (.pls) its File= entries in order (a name without a folder is beside it). */
    static File[] listed(File dir) {
        if (!isPls(dir)) return dir.listFiles();
        java.util.List<File> out = new java.util.ArrayList<File>();
        try {
            File beside = dir.getAbsoluteFile().getParentFile();
            for (String line : new String(java.nio.file.Files.readAllBytes(dir.toPath()), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                String t = line.replace("\r", "").trim();
                if (!t.toLowerCase(java.util.Locale.ROOT).matches("file\\d+=.+")) continue;
                String name = t.substring(t.indexOf('=') + 1).trim();
                File f = new File(name);
                if (!f.isAbsolute()) f = new File(beside, name);
                if (f.isFile()) out.add(f);
                else System.out.println("Not found: " + name);
            }
        } catch (java.io.IOException ex) {
            System.out.println("Could not read " + dir.getName() + ": " + ex.getMessage());
        }
        return out.toArray(new File[0]);
    }
}
