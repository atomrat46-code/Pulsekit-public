package pulsekit;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Installed AI apps PyJav can hand a prompt to. */
public final class Subsystem {
  private static final String[] GROK = {"ai.x.grok"};
  private static final String CHAT_URL = "https://chat.sogni.ai";
  private static final String[] CLAUDE = {"com.anthropic.claude"};

  private Subsystem() {}

  public static boolean grok(Context context) {
    return packageName(context, GROK) != null;
  }

  public static boolean sogni(Context context) {
    return true;
  }

  public static boolean claude(Context context) {
    return claudePackage(context) != null;
  }

  /** Installed subsystems, each row is {name, package}. */
  public static String[][] available(Context context) {
    ArrayList<String[]> rows = new ArrayList<String[]>();
    String grokPkg = packageName(context, GROK);
    String sogniPkg = sogniPackage(context);
    String claudePkg = claudePackage(context);
    if (grokPkg != null) rows.add(new String[] {"Grok", grokPkg});
    rows.add(new String[] {"Sogni Chat", sogniPkg != null ? sogniPkg : CHAT_URL});
    if (claudePkg != null) rows.add(new String[] {"Claude", claudePkg});
    return rows.toArray(new String[rows.size()][]);
  }

  public static String openPackage(Context context, String name, String pkg, String prompt, String ref1Path, String ref2Path) {
    if (context == null || pkg == null) return "AI prompt. PyJav cannot run this on Android.";
    String shown = name == null || name.length() == 0 ? "The subsystem" : name;
    String text = prompt == null ? "" : prompt;
    if (pkg.startsWith("http://") || pkg.startsWith("https://")) {
      if (text.length() > 0) copyPrompt(context, text);
      if (openUrl(context, pkg)) return "Opened " + shown + ". Prompt copied.";
      return shown + " could not be opened.";
    }
    boolean files = ref1Path != null && ref1Path.length() > 0 || ref2Path != null && ref2Path.length() > 0;
    return handoff(context, shown, pkg, text, ref1Path, ref2Path, files);
  }

  private static String handoff(Context context, String shown, String pkg, String text, String ref1Path, String ref2Path, boolean files) {
    if (text.length() > 0) copyPrompt(context, text);
    boolean type = files && text.length() > 0 && PromptInserter.enabled(context);
    if (type) PromptInserter.arm(pkg, text);
    if (files && send(context, pkg, text, ref1Path, ref2Path)) {
      return "Opened in " + shown + " with the reference file. Prompt copied.";
    }
    if (type) PromptInserter.disarm();
    if (send(context, pkg, text, null, null)) {
      return (files ? "Opened in " + shown + ". It did not accept the reference files. " : "Opened in " + shown + ". ") + (text.length() > 0 ? "Prompt copied." : "");
    }
    if (launch(context, pkg, text)) return "Opened " + shown + ". Prompt copied.";
    return shown + " is installed, but PyJav could not open it.";
  }

  public static void copyPrompt(Context context, String text) {
    if (context == null || text == null) return;
    try {
      ClipboardManager clips = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
      if (clips != null) clips.setPrimaryClip(ClipData.newPlainText("prompt", text));
    } catch (Exception ignored) {}
  }

  /** Opens the only installed subsystem. Several subsystems are chosen in PyJavUi. */
  public static String open(Context context, String category, String prompt, String ref1Path, String ref2Path) {
    String[][] found = available(context);
    if (found.length == 0) return "AI prompt. PyJav cannot run this on Android. Grok, Sogni, and Claude are not present.";
    return openPackage(context, found[0][0], found[0][1], prompt, ref1Path, ref2Path);
  }

  public static String open(Context context, String category, String prompt) {
    return open(context, category, prompt, null, null);
  }

  private static String packageName(Context context, String[] packages) {
    if (context == null) return null;
    PackageManager pm = context.getPackageManager();
    for (int i = 0; i < packages.length; i++) {
      try {
        pm.getPackageInfo(packages[i], 0);
        return packages[i];
      } catch (Exception ignored) {}
    }
    return null;
  }

  private static String sogniPackage(Context context) {
    return findChat(context);
  }

  /** Installed Sogni Chat only. Sogni Create is not used. */
  private static String findChat(Context context) {
    if (context == null) return null;
    PackageManager pm = context.getPackageManager();
    List<ApplicationInfo> apps;
    try {
      apps = pm.getInstalledApplications(0);
    } catch (Exception ex) {
      return null;
    }
    for (int i = 0; i < apps.size(); i++) {
      ApplicationInfo info = apps.get(i);
      if (info == null || info.packageName == null) continue;
      String pkg = info.packageName.toLowerCase();
      String label = "";
      try {
        label = String.valueOf(info.loadLabel(pm)).toLowerCase();
      } catch (Exception ignored) {}
      boolean chat = label.indexOf("sogni chat") >= 0 || (pkg.indexOf("sogni") >= 0 && pkg.indexOf("chat") >= 0);
      if (chat && label.indexOf("create") < 0 && pkg.indexOf("create") < 0) return info.packageName;
    }
    return null;
  }

  private static boolean openUrl(Context context, String url) {
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intent);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private static String claudePackage(Context context) {
    String known = packageName(context, CLAUDE);
    if (known != null) return known;
    return findApp(context, "claude", "anthropic");
  }

  /** Match an installed app by package or launcher name. prefer is chosen when several match. */
  private static String findApp(Context context, String token, String prefer) {
    if (context == null) return null;
    PackageManager pm = context.getPackageManager();
    List<ApplicationInfo> apps;
    try {
      apps = pm.getInstalledApplications(0);
    } catch (Exception ex) {
      return null;
    }
    String fallback = null;
    for (int i = 0; i < apps.size(); i++) {
      ApplicationInfo info = apps.get(i);
      if (info == null || info.packageName == null) continue;
      String pkg = info.packageName.toLowerCase();
      String label = "";
      try {
        label = String.valueOf(info.loadLabel(pm)).toLowerCase();
      } catch (Exception ignored) {}
      if (pkg.indexOf(token) < 0 && label.indexOf(token) < 0) continue;
      if (prefer != null && (pkg.indexOf(prefer) >= 0 || label.indexOf(prefer) >= 0)) return info.packageName;
      if (fallback == null || label.equals(token)) fallback = info.packageName;
    }
    return fallback;
  }

  private static boolean send(Context context, String pkg, String text, String ref1Path, String ref2Path) {
    try {
      ArrayList<String> paths = new ArrayList<String>();
      if (isFile(ref1Path)) paths.add(ref1Path);
      if (isFile(ref2Path)) paths.add(ref2Path);
      ArrayList<Uri> uris = new ArrayList<Uri>();
      for (int i = 0; i < paths.size(); i++) addUri(context, uris, paths.get(i));
      String[] types = uris.isEmpty() ? new String[] {"text/plain"} : shareTypes(paths);
      for (int i = 0; i < types.length; i++) {
        if (deliver(context, pkg, text, uris, types[i])) return true;
      }
      return false;
    } catch (Exception ex) {
      return false;
    }
  }

  private static boolean isFile(String path) {
    return path != null && path.length() > 0 && new File(path).isFile();
  }

  private static String[] shareTypes(ArrayList<String> paths) {
    String exact = RefProvider.typeOf(new File(paths.get(0)).getName());
    String family = familyOf(exact);
    boolean sameFamily = true;
    boolean sameExact = true;
    for (int i = 1; i < paths.size(); i++) {
      String next = RefProvider.typeOf(new File(paths.get(i)).getName());
      if (!familyOf(next).equals(family)) sameFamily = false;
      if (!next.equals(exact)) sameExact = false;
    }
    ArrayList<String> types = new ArrayList<String>();
    if (sameFamily && sameExact) types.add(exact);
    if (sameFamily && (family.equals("image") || family.equals("video") || family.equals("audio"))) types.add(family + "/*");
    types.add("*/*");
    return types.toArray(new String[types.size()]);
  }

  private static String familyOf(String mime) {
    int slash = mime.indexOf('/');
    return slash < 0 ? mime : mime.substring(0, slash);
  }

  private static boolean deliver(Context context, String pkg, String text, ArrayList<Uri> uris, String type) {
    try {
      Intent intent = new Intent(uris.size() > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
      intent.setType(type);
      intent.setPackage(pkg);
      intent.addCategory(Intent.CATEGORY_DEFAULT);
      intent.putExtra(Intent.EXTRA_TEXT, text);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
      if (uris.size() == 1) {
        intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        intent.setClipData(ClipData.newRawUri("reference", uris.get(0)));
        if (text != null && text.length() > 0) intent.getClipData().addItem(new ClipData.Item(text));
      } else if (uris.size() > 1) {
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        ClipData clip = ClipData.newRawUri("reference", uris.get(0));
        for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
        if (text != null && text.length() > 0) clip.addItem(new ClipData.Item(text));
        intent.setClipData(clip);
      }
      for (int i = 0; i < uris.size(); i++) context.grantUriPermission(pkg, uris.get(i), Intent.FLAG_GRANT_READ_URI_PERMISSION);
      if (context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) return false;
      context.startActivity(intent);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private static void addUri(Context context, ArrayList<Uri> uris, String path) {
    if (path == null || path.length() == 0) return;
    File file = new File(path);
    if (!file.isFile()) return;
    uris.add(Uri.parse("content://" + RefProvider.AUTHORITY + "/" + Uri.encode(file.getName())));
  }

  private static boolean send(Context context, String pkg, String text) {
    return send(context, pkg, text, null, null);
  }

  private static boolean launch(Context context, String pkg, String text) {
    try {
      ClipboardManager clips = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
      if (clips != null) clips.setPrimaryClip(ClipData.newPlainText("prompt", text));
      Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
      if (intent == null) return false;
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intent);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }
}
