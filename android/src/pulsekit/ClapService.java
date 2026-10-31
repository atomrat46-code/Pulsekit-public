package pulsekit;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;

/**
 * The CLAP host (libpulsekitclap.so) in a process of its own (":clap" in the manifest): a plugin
 * that crashes ends this process, not Pulsekit. ClapHost sends it a job (list a .clap's plugins,
 * render a MIDI file through one) and gets the answer back.
 */
public final class ClapService extends Service {
    static final int LIST = 1, RENDER = 2;

    static native String nativeList(String path);

    static native String nativeRender(String path, String id, String midi, String wav, int rate, double tail, double volume);

    private static String loadError;
    private static boolean loaded;

    /** The host library, once a process; null when it is there, else why not. */
    static synchronized String load() {
        if (loaded) return null;
        if (loadError != null) return loadError;
        try {
            System.loadLibrary("pulsekitclap");
            loaded = true;
            return null;
        } catch (Throwable ex) {
            loadError = "this build has no CLAP host for this phone (" + android.os.Build.SUPPORTED_ABIS[0] + "; the host is built for arm64-v8a): " + ex.getMessage();
            return loadError;
        }
    }

    /** One job: its answer ("ok", "info"; "list" for LIST). */
    static Bundle run(int what, Bundle job) {
        Bundle out = new Bundle();
        String missing = load();
        if (missing != null) {
            out.putBoolean("ok", false);
            out.putString("info", missing);
            return out;
        }
        String said = what == LIST ? nativeList(job.getString("path"))
            : nativeRender(job.getString("path"), job.getString("id", ""), job.getString("midi"), job.getString("wav"), job.getInt("rate", 44100),
                job.getDouble("tail", 2.0), job.getDouble("volume", 1.0));
        int tab = said.indexOf('\t');
        int n = Integer.parseInt(said.substring(0, tab));
        String text = said.substring(tab + 1);
        out.putBoolean("ok", what == LIST ? n >= 0 : n == 1);
        out.putString(what == LIST && n >= 0 ? "list" : "info", text);
        return out;
    }

    private HandlerThread worker;
    private Messenger messenger;

    @Override
    public void onCreate() {
        super.onCreate();
        this.worker = new HandlerThread("pulsekit-clap");
        this.worker.start();
        this.messenger = new Messenger(new Handler(this.worker.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Bundle out = run(msg.what, msg.getData());
                Message reply = Message.obtain(null, msg.what);
                reply.setData(out);
                try {
                    if (msg.replyTo != null) msg.replyTo.send(reply);
                } catch (Exception ignored) {
                    // Pulsekit went away
                }
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return this.messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        if (this.worker != null) this.worker.quitSafely();
        super.onDestroy();
    }
}
