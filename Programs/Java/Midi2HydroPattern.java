/*
 * Midi2HydroPattern is a Java version of midi2hydro_pattern.py (https://github.com/brandjamie),
 * which comes with this license:
 *
 * MIT License
 *
 * Copyright (c) 2017 brandjamie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Usage: java Midi2HydroPattern [input.mid] [output.h2pattern] [--category NAME] [--name NAME] [--kit NAME] [--dir FOLDER] [--recursive] [--outdir FOLDER]
 *
 * General MIDI drum patterns as Hydrogen patterns (.h2pattern). Put them in Hydrogen's patterns
 * folder (usually ~/.hydrogen/data/patterns) and Hydrogen lists them in its pattern library.
 * A Java version of midi2hydro_pattern.py by brandjamie (MIT License, above), with all the MIDI
 * files of a folder at once.
 *
 *   input.mid         the MIDI pattern (format 0 or 1).
 *   output.h2pattern  the pattern written; <input>.h2pattern when it is not given (as the script).
 *   --category NAME   the category Hydrogen shows it under (Uncategorized; a subfolder's name for
 *                     the files in it with --recursive).
 *   --name NAME       the pattern's name in Hydrogen (the file's name without .mid).
 *   --kit NAME        the drum kit the pattern is for: GMkit (default: instrument = note - 36) or
 *                     GMRockKit (Hydrogen's GMRockKit instruments, by their MIDI notes).
 *   --dir FOLDER      every .mid and .midi file in the folder, each to its own .h2pattern.
 *   --recursive       with --dir, its subfolders too.
 *   --outdir FOLDER   where the patterns go (next to each MIDI file when not given).
 *
 * Unlike the script, a note-on of velocity 0 (a note-off) is not written as a silent note, all the
 * MIDI events' delta times count (not only the notes'), only the drum channel (10) is taken when
 * the file has notes there, a note below the kit's first is left out, and a time signature other
 * than 4/4 is written as the pattern's denominator.
 *
 * Plain Java (no lambdas, java.* only), so PyJav can compile it on a phone.
 */
public class Midi2HydroPattern {
    /** Hydrogen's ticks per quarter note (the script's "magic number"). */
    static final int RES = 48;

    /** GMRockKit's MIDI out notes, by instrument number (as Hydrogen's kit lists them). */
    static final int[] GM_ROCK_KIT = {36, 37, 38, 39, 38, 41, 42, 43, 44, 45, 46, 56, 51, 49, 59, 57, 82, 81};

    private Midi2HydroPattern() {}

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
        System.out.println("Usage: java Midi2HydroPattern [input.mid] [output.h2pattern] [--category NAME] [--name NAME] [--kit NAME] [--dir FOLDER] [--recursive] [--outdir FOLDER]");
    }

    static int run(String[] args) throws Exception {
        List<String> plain = new ArrayList<String>();
        String category = null, name = null, kit = "GMkit", dir = null, outdir = null;
        boolean recursive = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            String v = i + 1 < args.length ? args[i + 1] : null;
            if (a.equals("--category") && v != null) { category = v; i++; }
            else if (a.equals("--name") && v != null) { name = v; i++; }
            else if (a.equals("--kit") && v != null) { kit = v; i++; }
            else if (a.equals("--dir") && v != null) { dir = v; i++; }
            else if (a.equals("--outdir") && v != null) { outdir = v; i++; }
            else if (a.equals("--recursive")) recursive = true;
            else if (a.equals("-h") || a.equals("--help")) { usage(); return 0; }
            else if (a.startsWith("--")) throw new IllegalArgumentException("Unknown option: " + a);
            else plain.add(a);
        }
        // As the script: input, output, category and name may also be given in order.
        if (plain.size() > 2 && category == null) category = plain.get(2);
        if (plain.size() > 3 && name == null) name = plain.get(3);
        Kit k = Kit.of(kit);
        File out = outdir == null || outdir.length() == 0 ? null : new File(outdir);
        // A relative folder is under the working folder (in Pulsekit on the phone, the run's own).
        // In the app the run's work folder (pulsekit.work: Android keeps user.dir at "/", read-only).
        String work = System.getProperty("pulsekit.work");
        if (work == null || work.length() == 0) work = System.getProperty("user.dir");
        if (out != null && !out.isAbsolute() && work != null) out = new File(work, outdir);
        if (out != null && !out.isDirectory() && !out.mkdirs()) throw new IOException("Cannot make the folder " + outdir);

        if (dir != null && dir.length() > 0) {
            File folder = new File(dir);
            if (!folder.isDirectory()) throw new IllegalArgumentException("Not a folder: " + dir);
            List<File> files = new ArrayList<File>();
            collect(folder, recursive, files);
            if (files.isEmpty()) {
                System.out.println("No .mid or .midi files in " + folder.getAbsolutePath());
                System.out.println("Failed: nothing to convert");
                return 1;
            }
            int ok = 0, failed = 0;
            for (File f : files) {
                String rel = relative(folder, f.getParentFile());
                String cat = category != null ? category : rel.length() > 0 ? rel.replace(File.separatorChar, '/') : "Uncategorized";
                File target;
                if (out == null) target = new File(f.getPath() + ".h2pattern");
                // Into one folder: a subfolder's files are named by it too, so none overwrites another.
                else target = new File(out, (rel.length() > 0 ? rel.replace(File.separatorChar, '-') + " - " : "") + f.getName() + ".h2pattern");
                try {
                    Result r = convert(f, target, cat, name != null && files.size() == 1 ? name : null, k);
                    System.out.println(r.line());
                    ok++;
                } catch (Exception ex) {
                    System.out.println(f.getName() + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
                    failed++;
                }
            }
            System.out.println(ok + " pattern" + (ok == 1 ? "" : "s") + " written" + (failed > 0 ? ", " + failed + " could not be read" : "")
                + (out != null ? " to " + out.getAbsolutePath() : ""));
            if (ok == 0) {
                System.out.println("Failed: no pattern written");
                return 1;
            }
            System.out.println("Succeeded: " + ok + " pattern" + (ok == 1 ? "" : "s"));
            return 0;
        }

        if (plain.isEmpty()) {
            usage();
            System.out.println("Failed: give a MIDI file, or --dir with a folder of them");
            return 2;
        }
        File in = new File(plain.get(0));
        File target;
        if (plain.size() > 1) {
            target = new File(plain.get(1));
            if (out != null && !target.isAbsolute()) target = new File(out, plain.get(1));
        } else {
            target = out == null ? new File(in.getPath() + ".h2pattern") : new File(out, in.getName() + ".h2pattern");
        }
        Result r = convert(in, target, category == null ? "Uncategorized" : category, name, k);
        System.out.println(r.line());
        System.out.println("Succeeded: " + target.getName());
        return 0;
    }

    static void collect(File folder, boolean recursive, List<File> out) {
        File[] kids = folder.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        for (File f : kids) {
            String low = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (low.endsWith(".mid") || low.endsWith(".midi"))) out.add(f);
        }
        if (recursive) for (File f : kids) if (f.isDirectory() && !f.getName().startsWith(".")) collect(f, true, out);
    }

    /** The folder's path under the base ("" for the base itself). */
    static String relative(File base, File folder) {
        String b = base.getAbsolutePath(), f = folder == null ? b : folder.getAbsolutePath();
        if (f.equals(b) || !f.startsWith(b)) return "";
        return f.substring(b.length()).replaceAll("^[\\\\/]+", "");
    }

    /** The drum kit a pattern is for: its name and the instrument a MIDI note plays (-1: none). */
    static final class Kit {
        final String name;
        final int[] notes;

        private Kit(String name, int[] notes) {
            this.name = name;
            this.notes = notes;
        }

        static Kit of(String name) {
            String n = name == null ? "" : name.trim();
            if (n.toLowerCase(Locale.ROOT).replace(" ", "").equals("gmrockkit")) return new Kit("GMRockKit", GM_ROCK_KIT);
            return new Kit(n.length() == 0 ? "GMkit" : n, null);
        }

        int instrument(int note) {
            if (notes == null) return note >= 36 ? note - 36 : -1;
            for (int i = 0; i < notes.length; i++) if (notes[i] == note) return i;
            return -1;
        }
    }

    static final class Result {
        String file, pattern;
        int notes, skipped, size, num = 4, den = 4;

        String line() {
            return file + ": " + notes + " notes" + (skipped > 0 ? " (" + skipped + " not in the kit, left out)" : "") + ", "
                + size + " ticks (" + fmt(size / (double) RES) + " quarter notes" + (den != 4 || num != 4 ? ", " + num + "/" + den : "") + ") -> " + pattern;
        }
    }

    static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    /** One MIDI file to one pattern. */
    static Result convert(File in, File target, String category, String name, Kit kit) throws IOException {
        Midi m = Midi.read(readAll(in));
        if (m.notes.isEmpty()) throw new IllegalArgumentException("no notes");
        boolean drums = false;
        for (int[] n : m.notes) if (n[1] == 9) drums = true;
        Result r = new Result();
        r.file = in.getName();
        r.pattern = target.getName();
        r.num = m.num;
        r.den = m.den;
        String patternName = name != null && name.length() > 0 ? name : in.getName().replaceAll("(?i)\\.midi?$", "");
        // The length: the last note's on or off, up to a whole quarter note (as the script).
        long quarters = (m.lastTick + m.ppq - 1) / m.ppq;
        r.size = (int) Math.max(1, quarters) * RES;
        StringBuilder sb = new StringBuilder();
        sb.append("<drumkit_pattern>\n");
        sb.append("    <pattern_for_drumkit>").append(xml(kit.name)).append("</pattern_for_drumkit>\n");
        sb.append("    <pattern>\n");
        sb.append("        <pattern_name>").append(xml(patternName)).append("</pattern_name>\n");
        sb.append("        <category>").append(xml(category)).append("</category>\n");
        sb.append("        <size>").append(r.size).append("</size>\n");
        if (m.den != 4) sb.append("        <denominator>").append(m.den).append("</denominator>\n");
        sb.append("        <noteList>\n");
        for (int[] n : m.notes) {
            if (drums && n[1] != 9) continue;
            int inst = kit.instrument(n[2]);
            if (inst < 0) {
                r.skipped++;
                continue;
            }
            long pos = Math.round(n[0] * (double) RES / m.ppq);
            sb.append("            <note>\n");
            sb.append("            <position>").append(pos).append("</position>\n");
            sb.append("            <leadlag>0</leadlag>\n");
            sb.append("            <velocity>").append(Double.toString(n[3] / 127.0)).append("</velocity>\n");
            sb.append("            <pan_L>0.5</pan_L>\n");
            sb.append("            <pan_R>0.5</pan_R>\n");
            sb.append("            <pitch>0</pitch>\n");
            sb.append("            <key>C0</key>\n");
            sb.append("            <length>-1</length>\n");
            sb.append("            <instrument>").append(inst).append("</instrument>\n");
            sb.append("            </note>\n");
            r.notes++;
        }
        sb.append("        </noteList>\n");
        sb.append("    </pattern>\n");
        sb.append("</drumkit_pattern>\n");
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        Writer w = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8);
        try {
            w.write(sb.toString());
        } finally {
            w.close();
        }
        return r;
    }

    static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** A MIDI file's note-ons {tick, channel, key, velocity}, its resolution, last note tick and time signature. */
    static final class Midi {
        int ppq = 480;
        long lastTick;
        int num = 4, den = 4;
        boolean sig;
        final List<int[]> notes = new ArrayList<int[]>();

        static Midi read(byte[] d) {
            if (d.length < 14 || d[0] != 'M' || d[1] != 'T' || d[2] != 'h' || d[3] != 'd') throw new IllegalArgumentException("not a MIDI file");
            Midi m = new Midi();
            int div = ((d[12] & 0xff) << 8) | (d[13] & 0xff);
            if ((div & 0x8000) != 0) throw new IllegalArgumentException("SMPTE-timed MIDI is not supported");
            m.ppq = div == 0 ? 480 : div;
            int i = 8 + be32(d, 4);
            while (i + 8 <= d.length) {
                int len = be32(d, i + 4);
                boolean track = d[i] == 'M' && d[i + 1] == 'T' && d[i + 2] == 'r' && d[i + 3] == 'k';
                int p = i + 8, end = Math.min(d.length, p + Math.max(0, len));
                i = end;
                if (!track) continue;
                long tick = 0;
                int status = 0;
                while (p < end) {
                    long delta = 0;
                    int b;
                    do {
                        b = d[p++] & 0xff;
                        delta = (delta << 7) | (b & 0x7f);
                    } while ((b & 0x80) != 0 && p < end);
                    tick += delta;
                    if (p >= end) break;
                    int s = d[p] & 0xff;
                    if (s == 0xff) {
                        if (p + 2 > end) break;
                        int type = d[p + 1] & 0xff;
                        p += 2;
                        long l = 0;
                        do {
                            b = d[p++] & 0xff;
                            l = (l << 7) | (b & 0x7f);
                        } while ((b & 0x80) != 0 && p < end);
                        if (type == 0x58 && l >= 2 && p + 1 < end && !m.sig) {
                            m.num = Math.max(1, d[p] & 0xff);
                            m.den = 1 << Math.min(5, d[p + 1] & 0xff);
                            m.sig = true;
                        }
                        p += (int) l;
                        continue;
                    }
                    if (s == 0xf0 || s == 0xf7) {
                        p++;
                        long l = 0;
                        do {
                            b = d[p++] & 0xff;
                            l = (l << 7) | (b & 0x7f);
                        } while ((b & 0x80) != 0 && p < end);
                        p += (int) l;
                        continue;
                    }
                    if ((s & 0x80) != 0) {
                        status = s;
                        p++;
                    }
                    if (status == 0) break;
                    int kind = status & 0xf0;
                    int n = kind == 0xc0 || kind == 0xd0 ? 1 : 2;
                    if (p + n > end) break;
                    if (kind == 0x90 || kind == 0x80) {
                        int key = d[p] & 0x7f, vel = d[p + 1] & 0x7f;
                        m.lastTick = Math.max(m.lastTick, tick);
                        if (kind == 0x90 && vel > 0) m.notes.add(new int[] {(int) tick, status & 0x0f, key, vel});
                    }
                    p += n;
                }
            }
            java.util.Collections.sort(m.notes, new java.util.Comparator<int[]>() {
                public int compare(int[] a, int[] b) {
                    return a[0] != b[0] ? (a[0] < b[0] ? -1 : 1) : a[2] - b[2];
                }
            });
            return m;
        }
    }

    static int be32(byte[] d, int i) {
        if (i + 4 > d.length) return 0;
        return ((d[i] & 0xff) << 24) | ((d[i + 1] & 0xff) << 16) | ((d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff);
    }

    static byte[] readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        } finally {
            in.close();
        }
    }
}
