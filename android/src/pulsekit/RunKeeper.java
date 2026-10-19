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
    String text = "Pulsekit · " + (program == null || program.isEmpty() ? "a program" : program) + " running";
    mark(context, program);
    try {
      Intent intent = new Intent(context, RunKeeper.class).putExtra(TEXT, text);
      if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
      else context.startService(intent);
    } catch (Throwable ignored) {
      // the phone does not allow it now: the run goes on without it
    }
  }

  /** Every run is over: the notification goes. */
  public static void stop(Context context) {
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

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    String text = intent == null ? null : intent.getStringExtra(TEXT);
    if (text == null) text = "Pulsekit · running";
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
    startForeground(1, b.build());
    foreground = true;
    if (stopWanted) {
      stopWanted = false;
      foreground = false;
      stopForeground(true);
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    foreground = false;
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
