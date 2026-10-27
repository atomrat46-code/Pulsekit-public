package pulsekit;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Python apps on the phone (Pydroid 3, QPython...). Android keeps each app's files to itself, so
 * Pulsekit cannot start their Python the way it starts python3: it hands the script over. The
 * program, midiutil.py, the input files the arguments name and a run_<program>.py that runs it with
 * those arguments go into Download/Pulsekit/PyJav/<program>, and run_<program>.py is opened in the
 * app (Run it there). What the program writes lands in that folder.
 */
final class PhonePython {
  private PhonePython() {}

  /** General settings' Python for scripts on the phone: the phone's own python3 first, as before. */
  static final String OWN = "";
  static final String FOLDER = "Pulsekit/PyJav";

  /** Pydroid 3 and the QPythons by their package, then any other app that calls itself Python. */
  static final String[][] KNOWN = {
    {"ru.iiec.pydroid3", "Pydroid 3"}, {"org.qpython.qpy", "QPython"}, {"org.qpython.qpy3", "QPython 3"},
    {"com.hipipal.qpyplus", "QPython+"}, {"org.qpython.qpyplus", "QPython Plus"},
  };

  /** A Python app: its package and its name. */
  static final class App {
    final String pkg;
    final String label;

    App(String pkg, String label) {
      this.pkg = pkg;
      this.label = label;
    }
  }

  /** The tests' apps (null: those installed). */
  static List<App> testApps;

  /** The Python apps installed: Pydroid 3 and QPython first, then others named Python. */
  static List<App> apps(Context ctx) {
    if (testApps != null) return testApps;
    List<App> out = new ArrayList<App>();
    PackageManager pm = ctx.getPackageManager();
    for (String[] k : KNOWN) {
      try {
        ApplicationInfo info = pm.getApplicationInfo(k[0], 0);
        CharSequence l = pm.getApplicationLabel(info);
        out.add(new App(k[0], l == null || l.length() == 0 ? k[1] : l.toString()));
      } catch (Exception ignored) {
        // not installed
      }
    }
    try {
      for (ApplicationInfo info : pm.getInstalledApplications(0)) {
        String p = info.packageName;
        boolean have = false;
        for (App a : out) have |= a.pkg.equals(p);
        if (have || p.equals(ctx.getPackageName())) continue;
        CharSequence l = pm.getApplicationLabel(info);
        String label = l == null ? "" : l.toString();
        String low = (p + " " + label).toLowerCase(Locale.ROOT);
        if (low.contains("pydroid") || low.contains("qpython") || label.toLowerCase(Locale.ROOT).matches(".*\\bpython\\b.*")) out.add(new App(p, label));
      }
    } catch (Exception ignored) {
      // the known ones only
    }
    return out;
  }

  /** The app General settings picked, while it is installed; null for the phone's own python3. */
  static App chosen(Context ctx) {
    String pick = AppSettings.pythonPath();
    if (pick.length() == 0) return null;
    for (App a : apps(ctx)) if (a.pkg.equals(pick)) return a;
    return null;
  }

  /** The tests' writer (null: Download through MediaStore). */
  interface Writer {
    /** Writes the file into the folder; where it is (a content:// address, or a path). */
    String write(String folder, String name, byte[] data) throws Exception;
  }

  static Writer testWriter;
  /** The hand-over opened last: the app, run_<program>.py's address and text (for the tests). */
  static Intent lastIntent;
  static String lastRunner;

  /** Download/Pulsekit/PyJav/<program> as a path the Python app reads (it asks for storage access once). */
  static String folderPath(String stem) {
    File down = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    return new File(new File(down, FOLDER), stem).getAbsolutePath();
  }

  /**
   * Hands the program to the app: its files and run_<program>.py into Download/Pulsekit/PyJav/<program>,
   * run_<program>.py opened there. The result says what happened and where the output goes.
   */
  static JavaRun.Result handOff(Context ctx, App app, String name, String source, List<String> argv, byte[] midiutil) {
    String stem = name.replaceAll("\\.[A-Za-z0-9]+$", "").replaceAll("[^A-Za-z0-9._-]+", "_");
    String folder = FOLDER + "/" + stem;
    String here = folderPath(stem);
    StringBuilder log = new StringBuilder();
    try {
      write(ctx, folder, name, source.getBytes(StandardCharsets.UTF_8));
      if (midiutil != null) write(ctx, folder, "midiutil.py", midiutil);
      // The input files the arguments name are copied beside it; an output file's path is put there too.
      List<String> args = new ArrayList<String>();
      if (argv != null) {
        for (String a : argv) {
          File f = a == null ? null : new File(a);
          boolean path = a != null && a.startsWith("/") && f.getName().contains(".");
          if (path && f.isFile()) {
            write(ctx, folder, f.getName(), java.nio.file.Files.readAllBytes(f.toPath()));
            log.append("copied ").append(f.getName()).append('\n');
            args.add(here + "/" + f.getName());
          } else if (path && (f.getParentFile() == null || !f.getParentFile().getAbsolutePath().startsWith(here))) {
            args.add(here + "/" + f.getName());
          } else {
            args.add(a);
          }
        }
      }
      String runner = runner(here, name, args);
      lastRunner = runner;
      String at = write(ctx, folder, "run_" + stem + ".py", runner.getBytes(StandardCharsets.UTF_8));
      Intent open = new Intent(Intent.ACTION_VIEW);
      if (at != null && at.startsWith("content:")) open.setDataAndType(Uri.parse(at), "text/x-python");
      else open.setDataAndType(Uri.fromFile(new File(here, "run_" + stem + ".py")), "text/x-python");
      open.setPackage(app.pkg);
      open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
      lastIntent = open;
      boolean opened = start(ctx, open);
      if (!opened) {
        // An app that opens no file: it is started, and run_<program>.py is opened from its own file list.
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(app.pkg);
        if (launch != null) {
          launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          lastIntent = launch;
          opened = start(ctx, launch);
        }
      }
      log.append(opened ? "Opened in " + app.label + ": run run_" + stem + ".py there (its Run button)."
          : app.label + " could not be opened: open run_" + stem + ".py in it from " + here + ".").append('\n');
      log.append("It runs ").append(name).append(args.isEmpty() ? "" : " " + String.join(" ", args)).append('\n');
      log.append("What it writes goes to ").append("Download/").append(folder).append(" (the Media browser shows it).\n");
      log.append("A missing library (No module named ...): install it in ").append(app.label).append(" (Pydroid 3: menu > Pip; QPython: its pip console), then run again.");
      return new JavaRun.Result("$ " + app.label + " " + name + "\n" + log, new ArrayList<JavaRun.FileOut>(), 0);
    } catch (Exception ex) {
      return new JavaRun.Result("Could not hand " + name + " to " + app.label + ": " + ex.getMessage(), new ArrayList<JavaRun.FileOut>(), 1);
    }
  }

  private static boolean start(Context ctx, Intent intent) {
    try {
      ctx.startActivity(intent);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  /** run_<program>.py: the folder as the working one, the arguments as sys.argv, the program run as __main__. */
  static String runner(String here, String name, List<String> args) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Pulsekit: runs ").append(name).append(" with the arguments PyJav gave, in this folder.\n");
    sb.append("import os, runpy, sys\n");
    sb.append("HERE = ").append(py(here)).append('\n');
    sb.append("os.chdir(HERE)\nsys.path.insert(0, HERE)\n");
    sb.append("sys.argv = [os.path.join(HERE, ").append(py(name)).append(")");
    for (String a : args) sb.append(", ").append(py(a));
    sb.append("]\n");
    sb.append("try:\n    runpy.run_path(sys.argv[0], run_name=\"__main__\")\n");
    sb.append("except ModuleNotFoundError as e:\n");
    sb.append("    print(\"\\n%s is not installed for this Python. Pydroid 3: menu > Pip, install %s. QPython: its pip console.\" % (e.name, e.name))\n");
    sb.append("    raise\n");
    return sb.toString();
  }

  /** A Python string literal. */
  static String py(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      if (c == '\\' || c == '"') sb.append('\\').append(c);
      else if (c == '\n') sb.append("\\n");
      else sb.append(c);
    }
    return sb.append('"').toString();
  }

  /** One file into Download/<folder> (an older one of the name is replaced); its address. */
  static String write(Context ctx, String folder, String name, byte[] data) throws Exception {
    if (testWriter != null) return testWriter.write(folder, name, data);
    if (Build.VERSION.SDK_INT >= 29) {
      String rel = Environment.DIRECTORY_DOWNLOADS + "/" + folder + "/";
      Uri table = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
      // The run's file from last time: replaced, not numbered "name (1)".
      ctx.getContentResolver().delete(table, MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[] {rel, name});
      ContentValues v = new ContentValues();
      v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
      v.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
      v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
      Uri uri = ctx.getContentResolver().insert(table, v);
      if (uri == null) throw new java.io.IOException("Download took no " + name);
      try (OutputStream out = ctx.getContentResolver().openOutputStream(uri, "wt")) {
        if (out == null) throw new java.io.IOException("no place for " + name);
        out.write(data);
      }
      return uri.toString();
    }
    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folder);
    if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("no folder " + dir);
    File f = new File(dir, name);
    java.nio.file.Files.write(f.toPath(), data);
    return f.getAbsolutePath();
  }
}
