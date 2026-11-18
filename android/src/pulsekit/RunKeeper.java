package pulsekit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Keeps Pulsekit running while a program runs (a Sogni download, say), as a notification
 * ("Pulsekit · SogniVideo running"): without it the phone may close Pulsekit when another app is
 * opened, and the run is lost. Also notes the run in running.txt, so that when Android closed
 * Pulsekit anyway, the next start says which run it was (CrashLog).
 */
public final class RunKeeper extends Service {
  static final String CHANNEL = "runs";
  static final String TEXT = "text";
  /** The run going on, kept while it goes: gone when it is over. */
  static final String MARK = "running.txt";

  /** A run started: the notification shows it. */
  public static void start(Context context, String program) {
    stopWanted = false;
    LATER.removeCallbacks(STOPPER);
    String text = "Pulsekit · " + (program == null || program.isEmpty() ? "a program" : program) + " running";
    mark(context, program);
    // Already shown (a chain's next run: SogniMusic's tracks through DrumMidi): only its text changed. Android
    // does not let an app in the background start it again; between runs it is kept (stop waits), so
    // DrumMidi in the background is not closed as a cached app using a whole core.
    if (foreground && service != null) {
      try {
        service.show(text);
        return;
      } catch (Throwable ignored) {
        // shown again below
      }
    }
    try {
      Intent intent = new Intent(context, RunKeeper.class).putExtra(TEXT, text);
      if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
      else context.startService(intent);
    } catch (Throwable ignored) {
      // the phone does not allow it now: the run goes on without it
    }
  }

  /** How long the notification stays after the last run, for a chain's next one (and again while one is coming). */
  static final long GRACE_MS = 60000L, MAX_IDLE_MS = 30 * 60000L;
  static volatile long stopAsked;
  static final android.os.Handler LATER = new android.os.Handler(android.os.Looper.getMainLooper());
  static Context stopContext;
  static final Runnable STOPPER = new Runnable() {
    @Override
    public void run() {
      // A chain's next step is coming (SogniMusic's tracks made WAVs, DrumMidi on each, a folder's file sets): kept.
      // (A chain's step between runs: at most MAX_IDLE_MS with no run, in case its state was left over.)
      if (PyJav.RUNS.get() > 0 || busy() && System.currentTimeMillis() - stopAsked < MAX_IDLE_MS) {
        LATER.postDelayed(this, GRACE_MS);
        return;
      }
      stopNow(stopContext);
    }
  };

  /** A chain of runs is not over (SogniMusic's comparison or file sets, a batch, a folder's file sets). */
  static boolean busy() {
    try {
      MusicCompare.Chain c = MusicCompare.chain();
      return c != null || MusicCompare.heldImport || BatchQueue.current() != null;
    } catch (Throwable ex) {
      return false;
    }
  }

  /** Every run is over: the notification goes (after GRACE_MS, so the next run of a chain keeps it). */
  public static void stop(Context context) {
    stopContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    stopAsked = System.currentTimeMillis();
    LATER.removeCallbacks(STOPPER);
    LATER.postDelayed(STOPPER, GRACE_MS);
  }

  static void stopNow(Context context) {
    if (context == null) return;
    unmark(context);
    // Stopped before it showed its notification (a run over at once, the app in the background):
    // Android ends the app when a service started that way never calls startForeground. It stops
    // itself once it has.
    if (!foreground) {
      stopWanted = true;
      return;
    }
    try {
      context.stopService(new Intent(context, RunKeeper.class));
    } catch (Throwable ignored) {
      // not running
    }
  }

  /** The notification is shown (startForeground was called); a stop asked for before then. */
  static volatile boolean foreground;
  static volatile boolean stopWanted;

  /** A run marked in this process: running.txt is this process's own, not one Android ended. */
  static volatile boolean markedHere;
  /** The notification permission was asked about (once). */
  static boolean asked;

  static void mark(Context context, String program) {
    markedHere = true;
    // Android 13 and later show the notification only when allowed: asked once.
    if (!asked && Build.VERSION.SDK_INT >= 33 && context instanceof android.app.Activity
        && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      asked = true;
      try {
        ((android.app.Activity) context).requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 4711);
      } catch (Throwable ignored) {
        // not asked
      }
    }
    try {
      java.nio.file.Files.write(new File(context.getFilesDir(), MARK).toPath(),
          ((program == null ? "" : program) + "\n" + System.currentTimeMillis() + "\n").getBytes(StandardCharsets.UTF_8));
    } catch (Throwable ignored) {
      // no note
    }
  }

  static void unmark(Context context) {
    new File(context.getFilesDir(), MARK).delete();
  }

  /** The service while it runs (show: its notification's text changed). */
  static volatile RunKeeper service;

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    String text = intent == null ? null : intent.getStringExtra(TEXT);
    if (text == null) text = "Pulsekit · running";
    service = this;
    startForeground(1, notification(text));
    foreground = true;
    if (stopWanted) {
      stopWanted = false;
      foreground = false;
      stopForeground(true);
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  /** The notification now says `text`. */
  void show(String text) {
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm != null) nm.notify(1, notification(text));
  }

  Notification notification(String text) {
    Notification.Builder b;
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Runs", NotificationManager.IMPORTANCE_LOW));
      }
      b = new Notification.Builder(this, CHANNEL);
    } else {
      b = new Notification.Builder(this);
    }
    b.setContentTitle(text).setContentText("Kept running until it is over").setSmallIcon(getApplicationInfo().icon).setOngoing(true);
    Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
    if (open != null) {
      b.setContentIntent(android.app.PendingIntent.getActivity(this, 0, open, Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0));
    }
    return b.build();
  }

  @Override
  public void onDestroy() {
    foreground = false;
    service = null;
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
