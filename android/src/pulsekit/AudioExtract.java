package pulsekit;

import android.net.Uri;
import java.io.File;

/**
 * Extract audio (a video thumbnail's item: the Media browser, Downloads, DB/Prompts): the video's
 * sound as <video>-audio.m4a, copied out of the MP4 without re-encoding, beside the video when its
 * folder takes files, else in Download (or the program files folder). Off the main thread; the
 * status line says where it went (or why not), then it asks whether to add it to the AudioMixer.
 */
final class AudioExtract {
    private AudioExtract() {}

    /** Where the file went last, and what was said (for the tests). */
    static volatile String last;
    static volatile String lastSaid;

    static byte[] read(File f) throws java.io.IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** The question shown last (for the tests). */
    static android.app.AlertDialog asked;

    /** After it: whether to add the sound to the AudioMixer as a track (off the main thread when yes). */
    static void offer(final MainActivity app, final String name, final byte[] sound) {
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        asked = new android.app.AlertDialog.Builder(app).setTitle(MixerOut.ASK_TITLE).setMessage(MixerOut.ask(name))
            .setPositiveButton(MixerOut.ADD, (d, w) -> {
                app.setNow("Adding " + name + " to the AudioMixer…");
                new Thread(() -> {
                    String said = MixerOut.addOne(name, sound);
                    lastSaid = said;
                    main.post(() -> app.setNow(said));
                }, "pulsekit-extract-mixer").start();
            }).setNegativeButton(MixerOut.NOT_NOW, null).show();
    }

    /** The video: its file (`path`), its document (`uri`) or its bytes (`bytes`, a library file). */
    static void run(final MainActivity app, final String name, final String path, final Uri uri, final byte[] bytes) {
        app.setNow("Extracting the audio of " + name + "…");
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            String said;
            String where = null;
            File tmp = null;
            byte[] sound = null;
            String soundName = null;
            try {
                File video = path != null ? new File(path) : null;
                if (video == null) {
                    // A document or a library file: read into the cache first.
                    tmp = new File(new File(app.getCacheDir(), "extract-audio"), name.replaceAll("[\\\\/:*?\"<>|]", "_"));
                    tmp.getParentFile().mkdirs();
                    java.io.InputStream in = bytes != null ? new java.io.ByteArrayInputStream(bytes) : app.getContentResolver().openInputStream(uri);
                    if (in == null) throw new java.io.IOException("it could not be read");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp);
                    try {
                        byte[] buf = new byte[1 << 16];
                        for (int n; (n = in.read(buf)) > 0; ) fos.write(buf, 0, n);
                    } finally {
                        fos.close();
                        in.close();
                    }
                    video = tmp;
                }
                File beside = path != null ? new File(path).getAbsoluteFile().getParentFile() : null;
                if (beside != null && beside.canWrite()) {
                    File made = Mp4Media.extractInto(video, name, beside);
                    where = made.getPath();
                    sound = read(made);
                    soundName = made.getName();
                } else {
                    File made = Mp4Media.extractInto(video, name, new File(app.getCacheDir(), "extract-audio-out"));
                    try {
                        byte[] data = read(made);
                        where = ArtJava.publish(app, made.getName(), data);
                        sound = data;
                        soundName = made.getName();
                    } finally {
                        made.delete();
                    }
                    if (where == null) throw new java.io.IOException("Download took no file");
                }
                said = "Extracted the audio of " + name + ": " + where;
            } catch (Exception ex) {
                said = "Could not extract the audio of " + name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } finally {
                if (tmp != null) tmp.delete();
            }
            final String shown = said;
            final byte[] got = where == null ? null : sound;
            final String gotName = soundName;
            last = where;
            lastSaid = said;
            main.post(() -> {
                app.setNow(shown);
                android.widget.Toast.makeText(app, shown, android.widget.Toast.LENGTH_SHORT).show();
                if (got != null) offer(app, gotName, got);
            });
        }, "pulsekit-extract-audio").start();
    }
}
