package pulsekit;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Types a prompt into Grok after the reference file share, which drops the text. */
public final class PromptInserter extends AccessibilityService {
  private static volatile PromptInserter live;
  private static volatile String pkg = "";
  private static volatile String text = "";
  private static volatile long until;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable retry = new Runnable() {
    public void run() {
      tryType();
    }
  };

  public static boolean enabled(Context context) {
    if (context == null) return false;
    String me = new ComponentName(context, PromptInserter.class).flattenToString();
    String on;
    try {
      on = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    } catch (Exception ex) {
      return false;
    }
    if (on == null || on.length() == 0) return false;
    String[] parts = on.split(":");
    for (int i = 0; i < parts.length; i++) {
      if (me.equalsIgnoreCase(parts[i])) return true;
    }
    return false;
  }

  public static void arm(String packageName, String prompt) {
    pkg = packageName == null ? "" : packageName;
    text = prompt == null ? "" : prompt;
    until = System.currentTimeMillis() + 12000L;
    PromptInserter service = live;
    if (service != null) service.schedule();
  }

  public static void disarm() {
    pkg = "";
    text = "";
    until = 0L;
  }

  @Override
  protected void onServiceConnected() {
    live = this;
    schedule();
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacks(retry);
    if (live == this) live = null;
    super.onDestroy();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event == null || event.getPackageName() == null) return;
    if (!event.getPackageName().toString().equals(pkg)) return;
    tryType();
  }

  @Override
  public void onInterrupt() {}

  private void schedule() {
    handler.removeCallbacks(retry);
    handler.postDelayed(retry, 350);
    handler.postDelayed(retry, 900);
    handler.postDelayed(retry, 1800);
    handler.postDelayed(retry, 3200);
  }

  private void tryType() {
    String want = pkg;
    String body = text;
    if (want.length() == 0 || body.length() == 0 || System.currentTimeMillis() > until) return;
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root == null) return;
    try {
      if (root.getPackageName() == null || !want.equals(root.getPackageName().toString())) return;
      AccessibilityNodeInfo field = findField(root, body);
      if (field == null) return;
      try {
        CharSequence have = field.getText();
        if (have != null && body.equals(have.toString())) {
          disarm();
          return;
        }
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, body);
        boolean ok = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        if (!ok) {
          ClipboardManager clips = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
          if (clips != null) clips.setPrimaryClip(ClipData.newPlainText("prompt", body));
          field.performAction(AccessibilityNodeInfo.ACTION_CLICK);
          ok = field.performAction(AccessibilityNodeInfo.ACTION_PASTE);
        }
        if (ok) disarm();
      } finally {
        field.recycle();
      }
    } finally {
      root.recycle();
    }
  }

  private AccessibilityNodeInfo findField(AccessibilityNodeInfo node, String body) {
    return walk(node, body, null, new int[] {-1});
  }

  private AccessibilityNodeInfo walk(AccessibilityNodeInfo node, String body, AccessibilityNodeInfo best, int[] score) {
    if (node == null) return best;
    if (usable(node, body)) {
      Rect rect = new Rect();
      node.getBoundsInScreen(rect);
      int rank = node.isFocused() ? 1000000 + rect.bottom : rect.bottom;
      if (rank >= score[0]) {
        if (best != null) best.recycle();
        best = AccessibilityNodeInfo.obtain(node);
        score[0] = rank;
      }
    }
    int count = node.getChildCount();
    for (int i = 0; i < count; i++) {
      AccessibilityNodeInfo child = node.getChild(i);
      if (child == null) continue;
      best = walk(child, body, best, score);
      child.recycle();
    }
    return best;
  }

  private boolean usable(AccessibilityNodeInfo node, String body) {
    if (!node.isVisibleToUser() || node.isPassword()) return false;
    String cls = node.getClassName() == null ? "" : node.getClassName().toString();
    boolean field = node.isEditable() || cls.indexOf("EditText") >= 0 || cls.indexOf("TextField") >= 0;
    if (!field) return false;
    String hint = "";
    if (android.os.Build.VERSION.SDK_INT >= 26 && node.getHintText() != null) hint = node.getHintText().toString();
    if (node.getContentDescription() != null) hint = hint + " " + node.getContentDescription();
    String lower = hint.toLowerCase(java.util.Locale.US);
    if (lower.indexOf("search") >= 0) return false;
    CharSequence have = node.getText();
    if (have == null || have.length() == 0) return true;
    return body.equals(have.toString());
  }
}
