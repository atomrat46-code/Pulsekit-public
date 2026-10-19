import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Usage: java MidiBatch [--dir <folder>] [--recursive] [--list <listfile>] [--outdir <folder>] [--max N] [--combine N] [--count N] [--cut N] [--seed N]
 *
 * A test batch from MIDI files you already have, for vst_render.py --dir <outdir> --test and then
 * CompareHits --batch <outdir> (as MidiDrumGen --count makes one from new grooves).
 *
 *   --dir        the MIDI files in this folder (*.mid, *.midi; not DrumMidi's own *-dm.mid);
 *                --recursive: in its subfolders too.
 *   --list       a text file listing MIDI files, one a line (# starts a comment; a name without a
 *                folder is beside the list). Every run writes list.txt into --outdir with the files it
 *                found, so a scanned folder can be trimmed by hand and given back with --list.
 *   --outdir     the batch folder (default midi-mix-<seed>): the MIDI files, list.txt and batch.txt
 *                (each file's source, tempo and time signature; base is the folder it came from).
 *   --max N      only N of the files, picked at random.
 *   --combine N  instead of copying the files, makes --count new ones (10), each N files picked at
 *                random played one after another: longer tests with changes of tempo, time
 *                signature and feel. --cut N plays only the first N bars of each.
 *   --seed N     the same seed picks the same files again (default: from the clock, printed).
 *
 * Plain Java (no lambdas, java.* only), so PyJav can compile it on a phone.
 */
public class MidiBatch {
    private MidiBatch() {}

    static final String USAGE = "Usage: java MidiBatch [--dir <folder>] [--recursive] [--list <listfile>] [--outdir <folder>] [--max N] [--combine N] [--count N] [--cut N] [--seed N]";
    /** Every file is written with this many ticks a quarter note. */
    static final int PPQ = 480;

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

    static int run(String[] args) throws Exception {
        String dir = null;
        String list = null;
        String outdir = null;
        boolean recursive = false;
        int max = 0;
        int combine = 0;
        int count = 10;
        int cut = 0;
        long seed = System.currentTimeMillis() % 1000000L;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            boolean more = i + 1 < args.length;
            if (a.equals("--dir") && more) dir = args[++i];
            else if (a.equals("--list") && more) list = args[++i];
            else if (a.equals("--outdir") && more) outdir = args[++i];
            else if (a.equals("--recursive")) recursive = true;
            else if (a.equals("--max") && more) max = number(a, args[++i], 1, 100000);
            else if (a.equals("--combine") && more) combine = number(a, args[++i], 2, 50);
            else if (a.equals("--count") && more) count = number(a, args[++i], 1, 10000);
            else if (a.equals("--cut") && more) cut = number(a, args[++i], 1, 1000);
            else if (a.equals("--seed") && more) seed = number(a, args[++i], 0, Integer.MAX_VALUE);
            else if (a.equals("-h") || a.equals("--help")) {
                System.out.println(USAGE);
                return 0;
            } else {
                System.out.println("Failed: unknown argument " + a);
                System.out.println(USAGE);
                return 2;
            }
        }
        if (dir == null && list == null) {
            System.out.println("Failed: give a folder of MIDI files (--dir) or a list of them (--list)");
            System.out.println(USAGE);
            return 2;
        }
        File out = new File(outdir != null && outdir.trim().length() > 0 ? outdir : "midi-mix-" + seed);
        List<File> files = new ArrayList<File>();
        if (dir != null) {
            File d = new File(dir);
            if (!d.isDirectory() && !isPls(d)) {
                System.out.println("Failed: no folder " + dir);
                return 2;
            }
            collect(d, recursive, out.getAbsoluteFile(), files);
            System.out.println("Found " + files.size() + " MIDI file" + (files.size() == 1 ? "" : "s") + " in " + d.getName() + (recursive ? " and its subfolders" : ""));
        }
        if (list != null) {
            List<File> listed = readList(new File(list));
            System.out.println("Listed " + listed.size() + " MIDI file" + (listed.size() == 1 ? "" : "s") + " in " + new File(list).getName());
            for (File f : listed) if (!files.contains(f)) files.add(f);
        }
        if (files.isEmpty()) {
            System.out.println("Failed: no MIDI files to use");
            return 1;
        }
        System.out.println("Seed: " + seed);
        Random rnd = new Random(seed);
        if (max > 0 && max < files.size()) {
            Collections.shuffle(files, rnd);
            files = new ArrayList<File>(files.subList(0, max));
        }
        // Read each once: a file that is not a MIDI (or has no notes) is left out, with why.
        List<Song> songs = new ArrayList<Song>();
        for (File f : files) {
            try {
                Song s = Song.read(f);
                if (s.notes == 0) System.out.println("Left out " + f.getName() + ": no notes");
                else songs.add(s);
            } catch (Exception ex) {
                System.out.println("Left out " + f.getName() + ": " + ex.getMessage());
            }
        }
        if (songs.isEmpty()) {
            System.out.println("Failed: none of the files could be read as MIDI");
            return 1;
        }
        if (combine > songs.size()) {
            System.out.println("Failed: --combine " + combine + " needs at least " + combine + " MIDI files (" + songs.size() + " found)");
            return 2;
        }
        out.mkdirs();
        StringBuilder listed = new StringBuilder("# MidiBatch seed " + seed + ": the MIDI files used (give back with --list)\n");
        for (Song s : songs) listed.append(s.file.getAbsolutePath()).append('\n');
        Files.write(new File(out, "list.txt").toPath(), listed.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder batch = new StringBuilder("file\tstyle\tbase\ttempo\ttimesig\thats\tparts\n");
        File root = dir == null ? null : new File(dir).getAbsoluteFile();
        int made = 0;
        if (combine == 0) {
            int n = 0;
            for (Song s : songs) {
                n++;
                String name = String.format(Locale.ROOT, "mix-%03d-%s.mid", n, clean(stem(s.file.getName())));
                Files.write(new File(out, name).toPath(), s.write(s.length(cut)));
                batch.append(name).append('\t').append(stem(s.file.getName())).append('\t').append(base(s.file, root)).append('\t')
                    .append(s.tempoText()).append('\t').append(s.timesig()).append("\t-\t").append(s.file.getName()).append('\n');
                System.out.println("Wrote " + name + String.format(Locale.ROOT, " (%s BPM, %s)", s.tempoText(), s.timesig()));
                made++;
            }
        } else {
            for (int n = 1; n <= count; n++) {
                List<Song> pick = new ArrayList<Song>(songs);
                Collections.shuffle(pick, rnd);
                pick = pick.subList(0, combine);
                Song joined = Song.join(pick, cut);
                String name = String.format(Locale.ROOT, "mix-%03d-%d-parts.mid", n, combine);
                Files.write(new File(out, name).toPath(), joined.write(joined.length(0)));
                StringBuilder parts = new StringBuilder();
                StringBuilder styles = new StringBuilder();
                String bases = null;
                String sigs = null;
                String tempos = null;
                for (Song s : pick) {
                    if (parts.length() > 0) {
                        parts.append(" + ");
                        styles.append('+');
                    }
                    parts.append(s.file.getName());
                    styles.append(stem(s.file.getName()));
                    String b = base(s.file, root);
                    bases = bases == null || bases.equals(b) ? b : "mixed";
                    sigs = sigs == null || sigs.equals(s.timesig()) ? s.timesig() : "mixed";
                    tempos = tempos == null || tempos.equals(s.tempoText()) ? s.tempoText() : "mixed";
                }
                batch.append(name).append('\t').append(styles).append('\t').append(bases).append('\t').append(tempos).append('\t').append(sigs)
                    .append("\t-\t").append(parts).append('\n');
                System.out.println("Wrote " + name + ": " + parts);
                made++;
            }
        }
        Files.write(new File(out, "batch.txt").toPath(), batch.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Wrote list.txt and batch.txt");
        System.out.println("Next: vst_render.py --dir \"" + out.getPath() + "\" --plugin <plugin> --test, then CompareHits --batch \"" + out.getPath() + "\"");
        System.out.println("Succeeded: " + made + " MIDI file" + (made == 1 ? "" : "s") + " in " + out.getPath());
        return 0;
    }

    static int number(String flag, String v, int lo, int hi) {
        try {
            int n = Integer.parseInt(v.trim());
            if (n >= lo && n <= hi) return n;
        } catch (NumberFormatException ignored) {
            // said below
        }
        throw new IllegalArgumentException(flag + " is " + lo + " to " + hi);
    }

    static boolean isMidi(String name) {
        String low = name.toLowerCase(Locale.ROOT);
        return (low.endsWith(".mid") || low.endsWith(".midi")) && !low.endsWith("-dm.mid");
    }

    /** The folder's MIDI files (by name), then its subfolders' when `recursive`; never the batch folder itself. */
    static void collect(File dir, boolean recursive, File skip, List<File> into) {
        if (dir.getAbsoluteFile().equals(skip)) return;
        File[] in = listed(dir);
        if (in == null) return;
        List<File> sorted = new ArrayList<File>();
        Collections.addAll(sorted, in);
        Collections.sort(sorted, new Comparator<File>() {
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File f : sorted) if (f.isFile() && isMidi(f.getName())) into.add(f.getAbsoluteFile());
        if (recursive) for (File f : sorted) if (f.isDirectory() && !f.getName().startsWith(".")) collect(f, true, skip, into);
    }

    static List<File> readList(File list) throws IOException {
        if (!list.isFile()) throw new IOException("no list " + list.getPath());
        List<File> out = new ArrayList<File>();
        // A playlist (.pls): its MIDI files.
        if (isPls(list)) {
            for (File f : listed(list)) if (isMidi(f.getName())) out.add(f.getAbsoluteFile());
            return out;
        }
        File beside = list.getAbsoluteFile().getParentFile();
        for (String line : new String(Files.readAllBytes(list.toPath()), StandardCharsets.UTF_8).split("\n")) {
            String t = line.replace("\r", "").trim();
            if (t.length() == 0 || t.startsWith("#")) continue;
            File f = new File(t);
            if (!f.isAbsolute()) f = new File(beside, t);
            if (f.isFile()) out.add(f.getAbsoluteFile());
            else System.out.println("Not found: " + t);
        }
        return out;
    }

    static String stem(String name) {
        return name.replaceAll("(?i)\\.midi?$", "");
    }

    static String clean(String s) {
        String c = s.replaceAll("[^A-Za-z0-9._-]+", "_");
        return c.length() > 40 ? c.substring(0, 40) : c;
    }

    /** The folder a file came from, under the scanned one ("." for the scanned folder itself). */
    static String base(File f, File root) {
        File parent = f.getParentFile();
        if (root == null || parent == null) return parent == null ? "." : parent.getName();
        String r = root.getAbsolutePath();
        String p = parent.getAbsolutePath();
        if (p.equals(r)) return ".";
        if (p.startsWith(r + File.separator)) return p.substring(r.length() + 1).replace(File.separatorChar, '/');
        return parent.getName();
    }

    /** One MIDI file's events on one time line (PPQ ticks): channel events, tempo and time signature. */
    static final class Song {
        File file;
        /** Each event: tick, then its bytes (status included); kept in order. */
        final List<long[]> ticks = new ArrayList<long[]>();
        final List<byte[]> events = new ArrayList<byte[]>();
        int notes;

        void add(long tick, byte[] e) {
            this.ticks.add(new long[] {tick, this.events.size()});
            this.events.add(e);
            if ((e[0] & 0xf0) == 0x90 && e.length > 2 && e[2] != 0) this.notes++;
        }

        /** The first tempo (BPM, rounded), as batch.txt shows it. */
        String tempoText() {
            for (int i = 0; i < this.events.size(); i++) {
                byte[] e = this.events.get(i);
                if (isMeta(e, 0x51) && e.length >= 6) {
                    int us = ((e[3] & 0xff) << 16) | ((e[4] & 0xff) << 8) | (e[5] & 0xff);
                    if (us > 0) return Long.toString(Math.round(60000000.0 / us));
                }
            }
            return "120";
        }

        /** The first time signature ("4/4" when none is given). */
        String timesig() {
            int[] ts = this.firstTimesig();
            return ts[0] + "/" + ts[1];
        }

        int[] firstTimesig() {
            for (byte[] e : this.events) {
                if (isMeta(e, 0x58) && e.length >= 5) return new int[] {Math.max(1, e[3] & 0xff), 1 << Math.min(6, e[4] & 0xff)};
            }
            return new int[] {4, 4};
        }

        long barTicks() {
            int[] ts = this.firstTimesig();
            return (long) PPQ * 4 * ts[0] / ts[1];
        }

        long lastTick() {
            long last = 0;
            for (long[] t : this.ticks) last = Math.max(last, t[0]);
            return last;
        }

        /** Where the song ends: after `bars` bars (0: all of it), else its last event rounded up to a whole bar. */
        long length(int bars) {
            long bar = this.barTicks();
            if (bars > 0) return bars * bar;
            long last = this.lastTick() + 1;
            return Math.max(bar, (last + bar - 1) / bar * bar);
        }

        /**
         * The songs one after another, each from where the one before ends (its whole bars, or `cut`
         * bars), each starting with its own tempo and time signature (120 BPM, 4/4 when it has none).
         */
        static Song join(List<Song> parts, int cut) {
            Song out = new Song();
            out.file = new File("joined.mid");
            long at = 0;
            for (Song s : parts) {
                long end = s.length(cut);
                boolean tempo = false;
                boolean sig = false;
                for (int i = 0; i < s.events.size(); i++) {
                    byte[] e = s.events.get(i);
                    if (s.ticks.get(i)[0] != 0) continue;
                    if (isMeta(e, 0x51)) tempo = true;
                    if (isMeta(e, 0x58)) sig = true;
                }
                if (!tempo) out.add(at, new byte[] {(byte) 0xff, 0x51, 3, 0x07, (byte) 0xa1, 0x20});
                if (!sig) out.add(at, new byte[] {(byte) 0xff, 0x58, 4, 4, 2, 24, 8});
                for (int i = 0; i < s.events.size(); i++) {
                    long t = s.ticks.get(i)[0];
                    if (t < end) out.add(at + t, s.events.get(i));
                }
                at += end;
            }
            return out;
        }

        static boolean isMeta(byte[] e, int type) {
            return e.length > 2 && (e[0] & 0xff) == 0xff && (e[1] & 0xff) == type;
        }

        /** Reads a format 0 or 1 MIDI file onto one time line, in PPQ ticks. */
        static Song read(File f) throws IOException {
            byte[] b = Files.readAllBytes(f.toPath());
            if (b.length < 14 || b[0] != 'M' || b[1] != 'T' || b[2] != 'h' || b[3] != 'd') throw new IOException("not a MIDI file");
            int division = ((b[12] & 0xff) << 8) | (b[13] & 0xff);
            if ((division & 0x8000) != 0 || division == 0) throw new IOException("SMPTE timing is not read");
            Song s = new Song();
            s.file = f;
            int p = 8 + (((b[4] & 0xff) << 24) | ((b[5] & 0xff) << 16) | ((b[6] & 0xff) << 8) | (b[7] & 0xff));
            while (p + 8 <= b.length) {
                int len = ((b[p + 4] & 0xff) << 24) | ((b[p + 5] & 0xff) << 16) | ((b[p + 6] & 0xff) << 8) | (b[p + 7] & 0xff);
                boolean track = b[p] == 'M' && b[p + 1] == 'T' && b[p + 2] == 'r' && b[p + 3] == 'k';
                int start = p + 8;
                int end = Math.min(b.length, start + Math.max(0, len));
                if (track) readTrack(b, start, end, division, s);
                p = end;
            }
            return s;
        }

        static void readTrack(byte[] b, int p, int end, int division, Song s) {
            long tick = 0;
            int running = 0;
            while (p < end) {
                long delta = 0;
                int c;
                do {
                    c = b[p++] & 0xff;
                    delta = (delta << 7) | (c & 0x7f);
                } while ((c & 0x80) != 0 && p < end);
                tick += delta;
                if (p >= end) break;
                int status = b[p] & 0xff;
                if (status < 0x80) status = running;
                else p++;
                long at = Math.round(tick * (double) PPQ / division);
                if (status == 0xff) {
                    if (p >= end) break;
                    int type = b[p++] & 0xff;
                    long n = 0;
                    do {
                        c = b[p++] & 0xff;
                        n = (n << 7) | (c & 0x7f);
                    } while ((c & 0x80) != 0 && p < end);
                    int from = p;
                    p = (int) Math.min(end, p + n);
                    if (type == 0x2f) break;
                    // Tempo and time signature are kept; names and text are left out.
                    if (type == 0x51 || type == 0x58) {
                        byte[] e = new byte[3 + (p - from)];
                        e[0] = (byte) 0xff;
                        e[1] = (byte) type;
                        e[2] = (byte) (p - from);
                        System.arraycopy(b, from, e, 3, p - from);
                        s.add(at, e);
                    }
                } else if (status == 0xf0 || status == 0xf7) {
                    long n = 0;
                    do {
                        c = b[p++] & 0xff;
                        n = (n << 7) | (c & 0x7f);
                    } while ((c & 0x80) != 0 && p < end);
                    p = (int) Math.min(end, p + n);
                } else if (status >= 0x80) {
                    running = status;
                    int kind = status & 0xf0;
                    int size = kind == 0xc0 || kind == 0xd0 ? 1 : 2;
                    if (p + size > end) break;
                    byte[] e = new byte[1 + size];
                    e[0] = (byte) status;
                    for (int i = 0; i < size; i++) e[1 + i] = b[p++];
                    s.add(at, e);
                } else {
                    // Data with no status before it: not a MIDI track after all.
                    break;
                }
            }
        }

        /**
         * The song as a format 0 MIDI file up to tick `end`: events at the same tick keep their order,
         * with note-offs first; a note still sounding at the end is let go there.
         */
        byte[] write(long end) throws IOException {
            List<long[]> order = new ArrayList<long[]>();
            for (int i = 0; i < this.ticks.size(); i++) {
                long[] t = this.ticks.get(i);
                if (t[0] < end) order.add(new long[] {t[0], isOff(this.events.get((int) t[1])) ? 0 : 1, i});
            }
            Collections.sort(order, new Comparator<long[]>() {
                public int compare(long[] a, long[] b) {
                    if (a[0] != b[0]) return a[0] < b[0] ? -1 : 1;
                    if (a[1] != b[1]) return a[1] < b[1] ? -1 : 1;
                    return a[2] < b[2] ? -1 : a[2] > b[2] ? 1 : 0;
                }
            });
            ByteArrayOutputStream track = new ByteArrayOutputStream();
            boolean[][] on = new boolean[16][128];
            long last = 0;
            for (long[] o : order) {
                byte[] e = this.events.get((int) this.ticks.get((int) o[2])[1]);
                int kind = e[0] & 0xf0;
                if (kind == 0x90 && e.length > 2 && e[2] != 0) on[e[0] & 0x0f][e[1] & 0x7f] = true;
                else if ((kind == 0x80 || kind == 0x90) && e.length > 1) on[e[0] & 0x0f][e[1] & 0x7f] = false;
                varLen(track, o[0] - last);
                last = o[0];
                track.write(e);
            }
            for (int ch = 0; ch < 16; ch++) {
                for (int n = 0; n < 128; n++) {
                    if (!on[ch][n]) continue;
                    varLen(track, end - 1 - last < 0 ? 0 : end - 1 - last);
                    last = Math.max(last, end - 1);
                    track.write(new byte[] {(byte) (0x80 | ch), (byte) n, 0});
                }
            }
            varLen(track, Math.max(0, end - last));
            track.write(new byte[] {(byte) 0xff, 0x2f, 0});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, (byte) (PPQ >> 8), (byte) PPQ});
            byte[] t = track.toByteArray();
            out.write(new byte[] {'M', 'T', 'r', 'k', (byte) (t.length >> 24), (byte) (t.length >> 16), (byte) (t.length >> 8), (byte) t.length});
            out.write(t);
            return out.toByteArray();
        }

        static boolean isOff(byte[] e) {
            int kind = e[0] & 0xf0;
            return kind == 0x80 || kind == 0x90 && e.length > 2 && e[2] == 0;
        }

        static void varLen(ByteArrayOutputStream out, long v) {
            long n = Math.max(0, v);
            int[] parts = new int[5];
            int k = 0;
            parts[k++] = (int) (n & 0x7f);
            while ((n >>= 7) > 0 && k < 5) parts[k++] = (int) ((n & 0x7f) | 0x80);
            for (int i = k - 1; i >= 0; i--) out.write(parts[i]);
        }
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
