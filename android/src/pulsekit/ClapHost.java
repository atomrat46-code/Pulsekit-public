package pulsekit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import java.io.File;
import java.util.function.Consumer;

/**
 * CLAP plugins on the phone: a .clap built for Android (arm64) imported into Pulsekit's own folder
 * (Android loads native code only from there), its plugins listed, and a MIDI file played through
 * one into a WAV, offline, by the host in its own process (ClapService): a plugin that crashes
 * does not take Pulsekit with it. No plugin window: it plays with its own defaults.
 */
final class ClapHost {
    private ClapHost() {}

    static final int IMPORT_CLAP = 95;
    static final int RENDER_MIDI = 96;
    static final int RATE = 44100;

    /** The tests: jobs run here, without the other process (null: ClapService). */
    static java.util.function.BiFunction<Integer, Bundle, Bundle> direct;
    /** What the last job said, for the tests. */
    static volatile String lastSaid = "";

    /** Pulsekit's CLAP folder (files/clap): where imported plugins are kept and loaded from. */
    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "clap");
        d.mkdirs();
        return d;
    }

    /** A picked .clap copied into Pulsekit's CLAP folder, read-only (as Android wants code it loads). Its file. */
    static File importClap(Context c, Uri uri, String name) throws Exception {
        String n = name == null || name.trim().isEmpty() ? "plugin.clap" : name.trim().replace('/', '_');
        if (!n.toLowerCase(java.util.Locale.ROOT).endsWith(".clap")) n += ".clap";
        File to = new File(dir(c), n);
        if (to.exists()) {
            to.setWritable(true);
            to.delete();
        }
        java.io.InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new java.io.IOException("no data");
        try {
            java.nio.file.Files.copy(in, to.toPath());
        } finally {
            in.close();
        }
        byte[] head = new byte[20];
        try (java.io.FileInputStream f = new java.io.FileInputStream(to)) {
            int got = f.read(head);
            if (got < 20 || head[0] != 0x7F || head[1] != 'E' || head[2] != 'L' || head[3] != 'F') {
                to.delete();
                throw new IllegalArgumentException(n + " is not a plugin built for Android (a Windows or Mac .clap does not run on a phone)");
            }
            // e_machine 183: AArch64, the phones' processor.
            int machine = (head[18] & 0xFF) | ((head[19] & 0xFF) << 8);
            if (machine != 183) {
                to.delete();
                throw new IllegalArgumentException(n + " is built for " + (machine == 62 ? "a Linux computer (x86-64)" : "another processor (" + machine + ")") + ", not for an Android phone (arm64)");
            }
        }
        to.setReadOnly();
        return to;
    }

    /**
     * Kit folders' CLAP folders: the .clap files in them (a folder picked with Android's picker, or
     * a path) copied into Pulsekit's CLAP folder when new or changed, 4 folders deep. What was done
     * ("" when nothing new).
     */
    private static final java.util.Set<String> REFUSED = new java.util.HashSet<String>();

    static String sync(Context c, java.util.List<String> folders) {
        int copied = 0;
        java.util.List<String> refused = new java.util.ArrayList<String>();
        for (String f : folders) {
            java.util.List<Object[]> found = new java.util.ArrayList<Object[]>();
            try {
                if (f.startsWith("content://")) {
                    Uri tree = Uri.parse(f);
                    findInTree(c, tree, android.provider.DocumentsContract.getTreeDocumentId(tree), 0, found);
                } else {
                    findInFolder(new File(f), 0, found);
                }
            } catch (Exception ex) {
                continue;
            }
            for (Object[] one : found) {
                String name = (String) one[0];
                long size = (Long) one[1];
                File have = new File(dir(c), name);
                if (have.isFile() && have.length() == size) continue;
                // A file already found not to be for this phone is said once (until it changes).
                if (REFUSED.contains(name + "\t" + size)) continue;
                try {
                    importClap(c, (Uri) one[2], name);
                    copied++;
                } catch (Exception ex) {
                    refused.add(name);
                    REFUSED.add(name + "\t" + size);
                }
            }
        }
        if (copied == 0 && refused.isEmpty()) return "";
        return (copied > 0 ? "Copied " + copied + " CLAP " + (copied == 1 ? "plugin" : "plugins") + " from the CLAP folders" : "")
            + (refused.isEmpty() ? "" : (copied > 0 ? "; " : "") + "not for this phone: " + String.join(", ", refused));
    }

    private static void findInFolder(File d, int depth, java.util.List<Object[]> out) {
        File[] kids = d.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isFile() && k.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".clap")) out.add(new Object[] {k.getName(), k.length(), Uri.fromFile(k)});
            else if (k.isDirectory() && depth < PluginLibrary.DEPTH && !k.getName().startsWith(".")) findInFolder(k, depth + 1, out);
        }
    }

    private static void findInTree(Context c, Uri tree, String doc, int depth, java.util.List<Object[]> out) {
        android.database.Cursor cur = c.getContentResolver().query(android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc),
            new String[] {android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE, android.provider.DocumentsContract.Document.COLUMN_SIZE}, null, null, null);
        if (cur == null) return;
        java.util.List<String> sub = new java.util.ArrayList<String>();
        try {
            while (cur.moveToNext()) {
                String id = cur.getString(0), name = cur.getString(1), mime = cur.getString(2);
                if (android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (depth < PluginLibrary.DEPTH && name != null && !name.startsWith(".")) sub.add(id);
                } else if (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".clap")) {
                    out.add(new Object[] {name, cur.isNull(3) ? -1L : cur.getLong(3), android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id)});
                }
            }
        } finally {
            cur.close();
        }
        for (String id : sub) findInTree(c, tree, id, depth + 1, out);
    }

    /** The plugins of a .clap: "id\tname\tvendor" lines, to `done` (on the screen's thread). */
    static void list(Context c, File clap, Consumer<Bundle> done) {
        Bundle job = new Bundle();
        job.putString("path", clap.getAbsolutePath());
        call(c, ClapService.LIST, job, done);
    }

    /** A MIDI file played through the plugin (`id`; empty: the file's first) into `wav`; the answer to `done`. */
    static void render(Context c, File clap, String id, File midi, File wav, double tail, double volume, Consumer<Bundle> done) {
        Bundle job = new Bundle();
        job.putString("path", clap.getAbsolutePath());
        job.putString("id", id == null ? "" : id);
        job.putString("midi", midi.getAbsolutePath());
        job.putString("wav", wav.getAbsolutePath());
        job.putInt("rate", RATE);
        job.putDouble("tail", tail);
        job.putDouble("volume", volume);
        call(c, ClapService.RENDER, job, done);
    }

    /** A job to ClapService's process; its answer (or that the process ended) to `done` on the screen's thread, once. */
    static void call(final Context c, final int what, final Bundle job, final Consumer<Bundle> done) {
        final Handler main = new Handler(Looper.getMainLooper());
        final boolean[] answered = {false};
        final Consumer<Bundle> once = b -> main.post(() -> {
            if (answered[0]) return;
            answered[0] = true;
            lastSaid = b.getString("info", b.getString("list", ""));
            done.accept(b);
        });
        if (direct != null) {
            new Thread(() -> once.accept(direct.apply(what, job)), "pulsekit-clap-direct").start();
            return;
        }
        final Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        final ServiceConnection[] conn = new ServiceConnection[1];
        final Messenger reply = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                once.accept(msg.getData());
                try {
                    app.unbindService(conn[0]);
                } catch (Exception ignored) {
                    // unbound already
                }
            }
        });
        conn[0] = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Message msg = Message.obtain(null, what);
                msg.setData(job);
                msg.replyTo = reply;
                try {
                    new Messenger(service).send(msg);
                } catch (Exception ex) {
                    once.accept(failed("could not reach the CLAP host: " + ex.getMessage()));
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // Its process ended before it answered: the plugin crashed.
                once.accept(failed("the plugin crashed (its own process ended; Pulsekit is fine)"));
                try {
                    app.unbindService(this);
                } catch (Exception ignored) {
                    // unbound already
                }
            }
        };
        if (!app.bindService(new Intent(app, ClapService.class), conn[0], Context.BIND_AUTO_CREATE)) {
            once.accept(failed("the CLAP host could not start"));
        }
    }

    static Bundle failed(String why) {
        Bundle b = new Bundle();
        b.putBoolean("ok", false);
        b.putString("info", why);
        return b;
    }

    /** Test: a short tune for an instrument (C, E, G, C an octave up, then the chord), 120 BPM, as a MIDI file. */
    static byte[] testMelody() {
        java.io.ByteArrayOutputStream t = new java.io.ByteArrayOutputStream();
        int[] keys = {60, 64, 67, 72};
        for (int i = 0; i < keys.length; i++) {
            // On (48 ticks after the one before; 480 a beat), off 432 ticks later.
            int[] ev = {i == 0 ? 0 : 48, 0x90, keys[i], 100, 0x83, 0x30, 0x80, keys[i], 0};
            for (int b : ev) t.write(b);
        }
        // The chord for two beats.
        int[] chord = {48, 0x90, 60, 90, 0, 0x90, 64, 90, 0, 0x90, 67, 90, 0x87, 0x40, 0x80, 60, 0, 0, 0x80, 64, 0, 0, 0x80, 67, 0};
        for (int b : chord) t.write(b);
        byte[] end = {0x00, (byte) 0xFF, 0x2F, 0x00};
        t.write(end, 0, end.length);
        byte[] body = t.toByteArray();
        java.io.ByteArrayOutputStream f = new java.io.ByteArrayOutputStream();
        byte[] head = {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x01, (byte) 0xE0, 'M', 'T', 'r', 'k'};
        f.write(head, 0, head.length);
        int n = body.length + 7;
        f.write(n >>> 24);
        f.write((n >>> 16) & 0xFF);
        f.write((n >>> 8) & 0xFF);
        f.write(n & 0xFF);
        byte[] tempo = {0x00, (byte) 0xFF, 0x51, 0x03, 0x07, (byte) 0xA1, 0x20};
        f.write(tempo, 0, tempo.length);
        f.write(body, 0, body.length);
        return f.toByteArray();
    }
}
