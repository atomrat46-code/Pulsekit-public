package pulsekit;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs JavaScript and TypeScript with Node inside Termux, when that app is installed. */
public final class ArtNode {
  private static final String TERMUX = "com.termux";
  private static final String NODE = "/data/data/com.termux/files/usr/bin/node";
  private static final String NPM = "/data/data/com.termux/files/usr/bin/npm";
  private static final String BASH = "/data/data/com.termux/files/usr/bin/bash";

  private ArtNode() {}

  public static JavaRun.Result run(String name, String source, List<String> argv) {
    Context ctx;
    try {
      ctx = ArtJava.context();
    } catch (Exception ex) {
      return fail("Android runtime is not ready yet.");
    }
    if (!installed(ctx)) {
      return fail("Node.js was not found.\nInstall Termux, then in Termux run: pkg install nodejs\nPyJav can use that Node.");
    }
    if (!granted(ctx) && !ask(ctx)) {
      return fail("Pulsekit asked Android for permission to run commands in Termux. Tap Allow if a request appears, then Run again.\nThat choice is not listed in Settings until Android has been asked.\nIn Termux, this is not an Android setting. Run:\nmkdir -p ~/.termux\necho allow-external-apps=true >> ~/.termux/termux.properties\npkg install nodejs");
    }
    String file = name == null || name.trim().isEmpty() ? "script.js" : name.replaceAll("[\\\\/]", "_");
    String src = source == null ? "" : source;
    boolean ts = file.toLowerCase().endsWith(".ts") || file.toLowerCase().endsWith(".mts") || file.toLowerCase().endsWith(".tsx");
    String script = shell(file, src, argv, ts);
    try {
      Intent back = termux(ctx, script);
      String err = back.getStringExtra("errmsg");
      if (err == null) err = "";
      String stdout = text(back, "stdout");
      String stderr = text(back, "stderr");
      int code = code(back);
      if (err.length() > 0 && stdout.length() == 0 && stderr.length() == 0) {
        return fail(explain(err));
      }
      String log = "$ termux node " + file + "\n" + stdout;
      if (stderr.length() > 0) log = log + (stdout.endsWith("\n") || stdout.length() == 0 ? "" : "\n") + stderr;
      List<JavaRun.FileOut> files = new ArrayList<JavaRun.FileOut>();
      log = takeFiles(log, files);
      if (code != 0 && log.indexOf("allow-external-apps") >= 0) return fail(explain(log));
      return new JavaRun.Result(log, files, code);
    } catch (Exception ex) {
      return fail("Could not run Node in Termux: " + ex.getMessage());
    }
  }

  private static String shell(String file, String src, List<String> argv, boolean ts) {
    String b64 = Base64.encodeToString(src.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP);
    StringBuilder sh = new StringBuilder();
    sh.append("set -e\n");
    sh.append("NODE=").append(NODE).append("\n");
    sh.append("NPM=").append(NPM).append("\n");
    sh.append("if [ ! -x \"$NODE\" ]; then echo 'Node.js is not installed in Termux. Open Termux and run: pkg install nodejs'; exit 127; fi\n");
    sh.append("mkdir -p \"$HOME/pulsekit-pyjav\"\n");
    sh.append("cd \"$HOME/pulsekit-pyjav\"\n");
    sh.append("printf '%s' '").append(b64).append("' | base64 -d > ").append(shq(file)).append("\n");
    java.util.LinkedHashSet<String> pkgs = JavaRun.nodePackages(src);
    if (!pkgs.isEmpty()) {
      StringBuilder json = new StringBuilder();
      json.append("{\"name\":\"pulsekit-pyjav\",\"private\":true,\"type\":");
      json.append(src.contains("import ") || src.contains("export ") || file.endsWith(".mjs") ? "\"module\"" : "\"commonjs\"");
      json.append(",\"dependencies\":{");
      boolean first = true;
      for (String pkg : pkgs) {
        if (!first) json.append(',');
        first = false;
        json.append('"').append(pkg.replace("\"", "")).append("\":\"*\"");
      }
      json.append("}}");
      sh.append("if [ ! -x \"$NPM\" ]; then echo 'npm was not found in Termux.'; exit 127; fi\n");
      sh.append("printf '%s' ").append(shq(json.toString())).append(" > package.json\n");
      sh.append("\"$NPM\" install --omit=dev --no-audit --no-fund\n");
    }
    sh.append("set +e\n");
    sh.append("\"$NODE\"");
    if (ts) sh.append(" --experimental-strip-types");
    sh.append(' ').append(shq(file));
    if (argv != null) {
      for (int i = 0; i < argv.size(); i++) sh.append(' ').append(shq(argv.get(i)));
    }
    sh.append("\nstatus=$?\n");
    sh.append("for f in *; do\n");
    sh.append("  [ -f \"$f\" ] || continue\n");
    sh.append("  case \"$f\" in package.json|package-lock.json|").append(file.replace("|", "")).append(") continue;; esac\n");
    sh.append("  echo \"PKFILE $f\"\n");
    sh.append("  base64 \"$f\"\n");
    sh.append("  echo PKEND\n");
    sh.append("done\n");
    sh.append("exit $status\n");
    return sh.toString();
  }

  private static boolean granted(Context ctx) {
    return ctx.checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED;
  }

  /** Asks with the system dialog. The Settings page stays empty until this runs. */
  private static boolean ask(Context ctx) {
    final Activity activity = activity();
    if (activity == null) return false;
    try {
      activity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          try {
            activity.requestPermissions(new String[] {"com.termux.permission.RUN_COMMAND"}, 77);
          } catch (Exception ignored) {}
        }
      });
    } catch (Exception ex) {
      return false;
    }
    long end = System.currentTimeMillis() + 40000L;
    while (System.currentTimeMillis() < end) {
      if (granted(ctx)) return true;
      try {
        Thread.sleep(250);
      } catch (InterruptedException ex) {
        return granted(ctx);
      }
    }
    return granted(ctx);
  }

  private static Activity activity() {
    try {
      Class<?> threadClass = Class.forName("android.app.ActivityThread");
      Object thread = threadClass.getMethod("currentActivityThread").invoke(null);
      java.lang.reflect.Field field = threadClass.getDeclaredField("mActivities");
      field.setAccessible(true);
      Object raw = field.get(thread);
      if (!(raw instanceof java.util.Map)) return null;
      Activity fallback = null;
      for (Object record : ((java.util.Map<?, ?>) raw).values()) {
        if (record == null) continue;
        java.lang.reflect.Field paused = record.getClass().getDeclaredField("paused");
        paused.setAccessible(true);
        java.lang.reflect.Field activity = record.getClass().getDeclaredField("activity");
        activity.setAccessible(true);
        Object item = activity.get(record);
        if (!(item instanceof Activity)) continue;
        if (!paused.getBoolean(record)) return (Activity) item;
        fallback = (Activity) item;
      }
      return fallback;
    } catch (Exception ex) {
      return null;
    }
  }

  private static Intent termux(Context ctx, String script) throws Exception {
    String action = "pulsekit.TERMUX_RESULT." + System.nanoTime();
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Intent> got = new AtomicReference<Intent>();
    BroadcastReceiver receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        got.set(intent);
        latch.countDown();
      }
    };
    IntentFilter filter = new IntentFilter(action);
    if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    else ctx.registerReceiver(receiver, filter);
    try {
      Intent callback = new Intent(action);
      callback.setPackage(ctx.getPackageName());
      int flags = PendingIntent.FLAG_UPDATE_CURRENT;
      if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
      PendingIntent pending = PendingIntent.getBroadcast(ctx, 0, callback, flags);
      Intent intent = new Intent();
      intent.setClassName(TERMUX, "com.termux.app.RunCommandService");
      intent.setAction("com.termux.RUN_COMMAND");
      intent.putExtra("com.termux.RUN_COMMAND_PATH", BASH);
      intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[] {"-lc", script});
      intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
      intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
      intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending);
      ctx.startService(intent);
      if (!latch.await(180, TimeUnit.SECONDS)) throw new Exception("Termux did not answer within 180s.");
      Intent back = got.get();
      if (back == null) throw new Exception("Termux returned nothing.");
      return back;
    } finally {
      try {
        ctx.unregisterReceiver(receiver);
      } catch (Exception ignored) {}
    }
  }

  private static String text(Intent in, String key) {
    if (in == null) return "";
    String direct = in.getStringExtra(key);
    if (direct != null) return direct;
    Bundle bundle = in.getBundleExtra("result");
    if (bundle != null) {
      String nested = bundle.getString(key);
      if (nested != null) return nested;
    }
    return "";
  }

  private static int code(Intent in) {
    if (in == null) return 1;
    if (in.hasExtra("exitCode")) return in.getIntExtra("exitCode", 1);
    Bundle bundle = in.getBundleExtra("result");
    if (bundle != null && bundle.containsKey("exitCode")) return bundle.getInt("exitCode", 1);
    return 0;
  }

  private static String takeFiles(String log, List<JavaRun.FileOut> files) {
    StringBuilder kept = new StringBuilder();
    int i = 0;
    while (i < log.length()) {
      int mark = log.indexOf("PKFILE ", i);
      if (mark < 0) {
        kept.append(log.substring(i));
        break;
      }
      kept.append(log.substring(i, mark));
      int nl = log.indexOf('\n', mark);
      if (nl < 0) break;
      String fname = log.substring(mark + 7, nl).trim();
      int end = log.indexOf("\nPKEND", nl);
      if (end < 0) {
        kept.append(log.substring(mark));
        break;
      }
      String b64 = log.substring(nl + 1, end).replace("\n", "").replace("\r", "");
      try {
        byte[] data = Base64.decode(b64, Base64.DEFAULT);
        if (data.length > 0 && data.length <= 3000000 && fname.length() > 0) files.add(new JavaRun.FileOut(fname, data));
      } catch (Exception ignored) {}
      i = end + 6;
      if (i < log.length() && log.charAt(i) == '\n') i++;
    }
    return kept.toString();
  }

  private static boolean installed(Context ctx) {
    try {
      ctx.getPackageManager().getPackageInfo(TERMUX, 0);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private static String explain(String err) {
    if (err == null) err = "";
    if (err.toLowerCase().indexOf("allow-external-apps") >= 0 || err.toLowerCase().indexOf("allow external") >= 0) {
      return "Termux blocked the command.\nIn Termux:\nmkdir -p ~/.termux\necho allow-external-apps=true >> ~/.termux/termux.properties\nThen Run again.\n" + err;
    }
    return err;
  }

  private static String shq(String s) {
    if (s == null) return "''";
    return "'" + s.replace("'", "'\\''") + "'";
  }

  private static JavaRun.Result fail(String log) {
    return new JavaRun.Result(log, Collections.<JavaRun.FileOut>emptyList(), 127);
  }
}
