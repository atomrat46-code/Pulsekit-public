package pulsekit;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * LV2 plugins on the phone: the bundles of an app that carries them built for Android (an aap-lv2
 * APK: its assets/lv2/<bundle>.lv2 descriptions and lib/arm64-v8a libraries), or of a zip with
 * <bundle>.lv2 folders, copied into Pulsekit's own folder (files/lv2; the libraries the plugins
 * need beside them in files/lv2/_libs). Lv2Bundle reads them; the host (lv2_render.c, in
 * ClapService's process) plays a MIDI file through an instrument, or a WAV through an effect.
 */
final class Lv2Host {
    private Lv2Host() {}

    static final int IMPORT_LV2 = 97;
    static final int PROCESS_WAV = 98;

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "lv2");
        d.mkdirs();
        return d;
    }

    static File libs(Context c) {
        File d = new File(dir(c), "_libs");
        d.mkdirs();
        return d;
    }

    /** The bundles of a picked APK or zip copied in; what was done. */
    static String importBundles(Context c, Uri uri, String name) throws Exception {
        File zip = new File(c.getCacheDir(), "lv2-import.zip");
        try (java.io.InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("no data");
            java.nio.file.Files.copy(in, zip.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            return importZip(c, zip, name == null ? zip.getName() : name);
        } finally {
            zip.delete();
        }
    }

    /** The bundles of an APK or zip file; what was done (throws when it holds none for this phone). */
    static String importZip(Context c, File zip, String name) throws Exception {
        java.util.Map<String, String> libsIn = new java.util.LinkedHashMap<String, String>();
        java.util.Set<String> bundles = new java.util.LinkedHashSet<String>();
        File root = dir(c);
        File work = new File(c.getCacheDir(), "lv2-import");
        deleteAll(work);
        work.mkdirs();
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> all = z.entries();
            while (all.hasMoreElements()) {
                java.util.zip.ZipEntry e = all.nextElement();
                String n = e.getName();
                if (e.isDirectory() || n.contains("..")) continue;
                // An Android library for the phone's processor: a plugin's or one it needs.
                if (n.startsWith("lib/arm64-v8a/") && n.endsWith(".so")) {
                    libsIn.put(n.substring(n.lastIndexOf('/') + 1), n);
                    continue;
                }
                int b = n.indexOf(".lv2/");
                if (b < 0) continue;
                int start = n.lastIndexOf('/', b) + 1;
                String bundle = n.substring(start, b + 4);
                bundles.add(bundle);
                File to = new File(work, bundle + "/" + n.substring(b + 5));
                to.getParentFile().mkdirs();
                try (java.io.InputStream in = z.getInputStream(e)) {
                    java.nio.file.Files.copy(in, to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (bundles.isEmpty()) throw new IllegalArgumentException(name + " has no LV2 bundles (an aap-lv2 APK, or a zip with <name>.lv2 folders)");
            int plugins = 0;
            List<String> done = new ArrayList<String>(), refused = new ArrayList<String>();
            for (String bundle : bundles) {
                File here = new File(work, bundle);
                List<Lv2Bundle.Plugin> ps;
                try {
                    ps = Lv2Bundle.read(here);
                } catch (Exception ex) {
                    refused.add(bundle + " (" + ex.getMessage() + ")");
                    continue;
                }
                // Each plugin's library: in the bundle already (a zip), or from the APK's lib/arm64-v8a.
                boolean ok = !ps.isEmpty();
                for (Lv2Bundle.Plugin p : ps) {
                    if (p.binary == null) continue;
                    File lib = new File(here, p.binary.getName());
                    if (!lib.isFile() && libsIn.containsKey(lib.getName())) {
                        try (java.io.InputStream in = z.getInputStream(z.getEntry(libsIn.get(lib.getName())))) {
                            java.nio.file.Files.copy(in, lib.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    String why = lib.isFile() ? arm64(lib) : "its library " + lib.getName() + " is not in it";
                    if (why != null) {
                        refused.add(bundle + " (" + why + ")");
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;
                File dest = new File(root, bundle);
                deleteAll(dest);
                if (!here.renameTo(dest)) throw new java.io.IOException("could not keep " + bundle);
                for (File f : dest.listFiles()) if (f.getName().endsWith(".so")) f.setReadOnly();
                done.add(bundle.replaceAll("\\.lv2$", ""));
                plugins += ps.size();
            }
            // The other libraries (libc++_shared.so...): loaded when a plugin needs them.
            File libs = libs(c);
            java.util.Set<String> binaries = new java.util.HashSet<String>();
            for (String bundle : done) {
                File[] fs = new File(root, bundle + ".lv2").listFiles();
                if (fs != null) for (File f : fs) binaries.add(f.getName());
            }
            for (java.util.Map.Entry<String, String> e : libsIn.entrySet()) {
                if (binaries.contains(e.getKey()) || done.isEmpty()) continue;
                File to = new File(libs, e.getKey());
                if (to.exists()) {
                    to.setWritable(true);
                    to.delete();
                }
                try (java.io.InputStream in = z.getInputStream(z.getEntry(e.getValue()))) {
                    java.nio.file.Files.copy(in, to.toPath());
                }
                // Only libraries this phone can load (a left-out bundle's computer library is not one).
                if (arm64(to) != null) {
                    to.delete();
                    continue;
                }
                to.setReadOnly();
            }
            if (done.isEmpty()) throw new IllegalArgumentException(name + ": no bundle for this phone: " + String.join("; ", refused));
            return "Imported " + plugins + " LV2 " + (plugins == 1 ? "plugin" : "plugins") + " (" + String.join(", ", done) + ")"
                + (refused.isEmpty() ? "" : "; left out " + String.join("; ", refused));
        } finally {
            deleteAll(work);
        }
    }

    /** null when the library is built for an Android phone (arm64), else what it is. */
    static String arm64(File lib) {
        byte[] head = new byte[20];
        try (java.io.FileInputStream f = new java.io.FileInputStream(lib)) {
            if (f.read(head) < 20 || head[0] != 0x7F || head[1] != 'E' || head[2] != 'L' || head[3] != 'F') return lib.getName() + " is not a library for Android";
        } catch (Exception ex) {
            return ex.getMessage();
        }
        int machine = (head[18] & 0xFF) | ((head[19] & 0xFF) << 8);
        return machine == 183 ? null : lib.getName() + " is built for " + (machine == 62 ? "a computer (x86-64)" : machine == 40 ? "32-bit phones" : "another processor") + ", not arm64";
    }

    static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        f.setWritable(true);
        f.delete();
    }

    /** A MIDI file through an instrument, or (midi null) a WAV through an effect, into `wav`; the answer to `done`. */
    static void render(Context c, Lv2Bundle.Plugin p, File midi, File wavIn, File wav, double tail, Consumer<Bundle> done) {
        Bundle job = new Bundle();
        job.putString("path", p.binary.getAbsolutePath());
        job.putString("id", p.uri);
        job.putString("bundle", p.bundle.getAbsolutePath());
        job.putString("libdir", libs(c).getAbsolutePath());
        job.putIntArray("kinds", p.kinds());
        job.putIntArray("outs", p.outs());
        job.putFloatArray("values", p.values());
        job.putString("midi", midi == null ? "" : midi.getAbsolutePath());
        job.putString("wavIn", wavIn == null ? "" : wavIn.getAbsolutePath());
        job.putString("wav", wav.getAbsolutePath());
        job.putInt("rate", ClapHost.RATE);
        job.putDouble("tail", tail);
        job.putDouble("volume", 1.0);
        ClapHost.call(c, ClapService.RENDER_LV2, job, done);
    }

    /** Test for an effect: two bars of a simple beat (a kick, a snare, hats) at 120 BPM, 44.1 kHz. */
    static short[] testBeat() {
        int sr = ClapHost.RATE, beat = sr / 2, n = beat * 8;
        short[] out = new short[n];
        java.util.Random noise = new java.util.Random(7);
        for (int b = 0; b < 8; b++) {
            int at = b * beat;
            boolean kick = b % 2 == 0, snare = b % 2 == 1;
            for (int i = 0; i < beat; i++) {
                double t = (double) i / sr, v = 0;
                if (kick) v += Math.sin(2 * Math.PI * (55 + 90 * Math.exp(-t * 30)) * t) * Math.exp(-t * 9) * 0.8;
                if (snare) v += (noise.nextDouble() * 2 - 1) * Math.exp(-t * 18) * 0.45 + Math.sin(2 * Math.PI * 190 * t) * Math.exp(-t * 20) * 0.3;
                int half = i - beat / 2;
                if (half >= 0) v += (noise.nextDouble() * 2 - 1) * Math.exp(-(double) half / sr * 60) * 0.15;
                else v += (noise.nextDouble() * 2 - 1) * Math.exp(-t * 60) * 0.15;
                out[at + i] = (short) Math.max(-32767, Math.min(32767, Math.round(v * 26000)));
            }
        }
        return out;
    }
}
