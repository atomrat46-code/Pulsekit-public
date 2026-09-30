package pulsekit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Prompts tab. Built outside javassist so the form is ordinary Java. */
public final class PromptSheet {
  private static final int MAX = 16 * 1024 * 1024;
  private static EditText title;
  private static EditText prompt;
  private static EditText out1;
  private static EditText out2;
  private static TextView ref1;
  private static TextView ref2;
  private static int picking;
  private static byte[] ref1Bytes;
  private static byte[] ref2Bytes;
  private static String ref1Name = "";
  private static String ref2Name = "";
  private static byte[] exportBytes;
  private static String exportName = "prompt.prompt";

  private PromptSheet() {}

  public static LinearLayout create(Activity activity) {
    int pad = dp(activity, 16);
    LinearLayout col = new LinearLayout(activity);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setBackgroundColor(Color.parseColor("#0A0B0C"));
    col.setPadding(pad, dp(activity, 12), pad, pad);
    col.setClickable(true);
    col.addView(label(activity, "Prompts", 20, "#ECEBE6", true));
    TextView lead = label(activity, "Name the prompt, then Save exports that name as a .prompt file. Open it on PyJav and Run.", 14, "#8A8B86", false);
    lead.setPadding(0, dp(activity, 4), 0, dp(activity, 12));
    col.addView(lead);
    col.addView(caption(activity, "PROMPT NAME"));
    title = line(activity);
    col.addView(title, lineLp(activity));
    ref1 = fileName(activity);
    col.addView(fileBlock(activity, "Reference file 1", ref1, 1));
    ref2 = fileName(activity);
    col.addView(fileBlock(activity, "Reference file 2", ref2, 2));
    col.addView(caption(activity, "PROMPT"));
    prompt = new EditText(activity);
    prompt.setMinLines(10);
    prompt.setGravity(Gravity.TOP);
    prompt.setTextColor(Color.parseColor("#ECEBE6"));
    prompt.setHintTextColor(Color.parseColor("#5C5D59"));
    prompt.setBackground(box(activity));
    prompt.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
    LinearLayout.LayoutParams promptLp = new LinearLayout.LayoutParams(-1, dp(activity, 220));
    promptLp.bottomMargin = dp(activity, 12);
    col.addView(prompt, promptLp);
    out1 = line(activity);
    out2 = line(activity);
    col.addView(caption(activity, "OUTPUT FILE 1"));
    col.addView(out1, lineLp(activity));
    col.addView(caption(activity, "OUTPUT FILE 2"));
    col.addView(out2, lineLp(activity));
    TextView save = new TextView(activity);
    save.setText("Save");
    save.setTextColor(Color.parseColor("#0A0B0C"));
    save.setTextSize(15);
    save.setGravity(Gravity.CENTER);
    save.setBackground(fill(activity, "#ECEBE6"));
    save.setOnClickListener(v -> save(activity));
    col.addView(save, new LinearLayout.LayoutParams(-1, dp(activity, 48)));
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(col);
    LinearLayout host = new LinearLayout(activity);
    host.setOrientation(LinearLayout.VERTICAL);
    host.setBackgroundColor(Color.parseColor("#0A0B0C"));
    host.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    load(activity);
    return host;
  }

  public static void beginPick(Activity activity, int which) {
    picking = which;
    Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
    intent.addCategory("android.intent.category.OPENABLE");
    intent.setType("*/*");
    activity.startActivityForResult(intent, 27);
  }

  public static void take(Activity activity, Uri uri) {
    int which = picking == 2 ? 2 : 1;
    String name = displayName(activity, uri);
    try {
      InputStream in = activity.getContentResolver().openInputStream(uri);
      if (in == null) throw new IllegalArgumentException("Could not open that file");
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[65536];
      int total = 0;
      int n;
      try {
        while ((n = in.read(buf)) > 0) {
          total += n;
          if (total > MAX) {
            Toast.makeText(activity, (which == 1 ? "Reference file 1" : "Reference file 2") + " is too large (max 16 MB)", 1).show();
            return;
          }
          bos.write(buf, 0, n);
        }
      } finally {
        in.close();
      }
      if (which == 1) {
        ref1Bytes = bos.toByteArray();
        ref1Name = name;
        if (ref1 != null) ref1.setText(name);
      } else {
        ref2Bytes = bos.toByteArray();
        ref2Name = name;
        if (ref2 != null) ref2.setText(name);
      }
    } catch (Exception ex) {
      String m = ex.getMessage();
      Toast.makeText(activity, m != null && m.length() > 0 ? m : "Could not read that file", 1).show();
    }
  }

  public static void save(Activity activity) {
    try {
      String name = text(title).replace('\n', ' ').replace('\r', ' ').trim();
      String fileName = PromptRun.fileName(name);
      if (fileName.length() == 0) {
        Toast.makeText(activity, "Name the prompt", 1).show();
        return;
      }
      String o1 = text(out1).replace('\n', ' ').replace('\r', ' ');
      String o2 = text(out2).replace('\n', ' ').replace('\r', ' ');
      String r1 = ref1Name == null ? "" : ref1Name;
      String r2 = ref2Name == null ? "" : ref2Name;
      String body = text(prompt);
      String sheet = PromptRun.encode(name, o1, o2, r1, r2, body);
      write(new File(activity.getFilesDir(), "prompts.txt"), sheet.getBytes(StandardCharsets.UTF_8));
      if (ref1Bytes != null) write(new File(activity.getFilesDir(), "prompt-ref-1.bin"), ref1Bytes);
      if (ref2Bytes != null) write(new File(activity.getFilesDir(), "prompt-ref-2.bin"), ref2Bytes);
      exportBytes = sheet.getBytes(StandardCharsets.UTF_8);
      exportName = fileName;
      Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
      intent.addCategory("android.intent.category.OPENABLE");
      intent.setType("text/plain");
      intent.putExtra("android.intent.extra.TITLE", fileName);
      activity.startActivityForResult(intent, 28);
    } catch (Exception ex) {
      String m = ex.getMessage();
      Toast.makeText(activity, m != null && m.length() > 0 ? m : "Could not save", 1).show();
    }
  }

  public static void writeExport(Activity activity, Uri uri) {
    if (uri == null || exportBytes == null) return;
    try {
      java.io.OutputStream out = activity.getContentResolver().openOutputStream(uri);
      if (out == null) {
        Toast.makeText(activity, "Could not save that prompt", 1).show();
        return;
      }
      try {
        out.write(exportBytes);
      } finally {
        out.close();
      }
      Toast.makeText(activity, "Saved · " + exportName, 0).show();
    } catch (Exception ex) {
      String m = ex.getMessage();
      Toast.makeText(activity, m != null && m.length() > 0 ? m : "Could not save", 1).show();
    }
  }

  private static void load(Activity activity) {
    File file = new File(activity.getFilesDir(), "prompts.txt");
    if (!file.isFile()) return;
    try {
      byte[] raw = readAll(file);
      if (raw.length > MAX) return;
      String text = new String(raw, StandardCharsets.UTF_8);
      PromptRun.Sheet sheet = PromptRun.parse(text);
      if (sheet == null) return;
      if (title != null) title.setText(sheet.name);
      if (out1 != null) out1.setText(sheet.out1);
      if (out2 != null) out2.setText(sheet.out2);
      ref1Name = sheet.ref1;
      ref2Name = sheet.ref2;
      if (ref1 != null && ref1Name.length() > 0) ref1.setText(ref1Name);
      if (ref2 != null && ref2Name.length() > 0) ref2.setText(ref2Name);
      if (prompt != null) prompt.setText(sheet.body);
    } catch (Exception ignored) {
      /* keep the empty sheet */
    }
  }

  private static LinearLayout fileBlock(Activity activity, String title, TextView name, int which) {
    LinearLayout box = new LinearLayout(activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(caption(activity, title.toUpperCase()));
    box.addView(name);
    TextView pick = new TextView(activity);
    pick.setText("Choose file");
    pick.setTextColor(Color.parseColor("#ECEBE6"));
    pick.setGravity(Gravity.CENTER);
    pick.setBackground(fill(activity, "#1B1D1F"));
    pick.setOnClickListener(v -> beginPick(activity, which));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
    lp.topMargin = dp(activity, 6);
    lp.bottomMargin = dp(activity, 12);
    box.addView(pick, lp);
    return box;
  }

  private static TextView fileName(Activity activity) {
    TextView view = new TextView(activity);
    view.setText("No file selected");
    view.setTextColor(Color.parseColor("#ECEBE6"));
    view.setTextSize(13);
    view.setTypeface(android.graphics.Typeface.MONOSPACE);
    view.setBackground(box(activity));
    view.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));
    return view;
  }

  private static EditText line(Activity activity) {
    EditText field = new EditText(activity);
    field.setSingleLine(true);
    field.setTextColor(Color.parseColor("#ECEBE6"));
    field.setBackground(box(activity));
    field.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
    return field;
  }

  private static LinearLayout.LayoutParams lineLp(Activity activity) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
    lp.bottomMargin = dp(activity, 10);
    return lp;
  }

  private static TextView caption(Activity activity, String text) {
    TextView view = label(activity, text, 11, "#5C5D59", true);
    view.setPadding(0, 0, 0, dp(activity, 4));
    return view;
  }

  private static TextView label(Activity activity, String text, int sp, String color, boolean bold) {
    TextView view = new TextView(activity);
    view.setText(text);
    view.setTextSize(sp);
    view.setTextColor(Color.parseColor(color));
    if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    return view;
  }

  private static GradientDrawable box(Activity activity) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.parseColor("#131416"));
    d.setCornerRadius(dp(activity, 8));
    d.setStroke(dp(activity, 1), Color.parseColor("#2A2B2C"));
    return d;
  }

  private static GradientDrawable fill(Activity activity, String color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.parseColor(color));
    d.setCornerRadius(dp(activity, 8));
    return d;
  }

  private static String displayName(Activity activity, Uri uri) {
    String name = null;
    android.database.Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null);
    if (cursor != null) {
      try {
        if (cursor.moveToFirst()) {
          int col = cursor.getColumnIndex("_display_name");
          if (col >= 0) name = cursor.getString(col);
        }
      } finally {
        cursor.close();
      }
    }
    if (name == null || name.length() == 0) name = uri.getLastPathSegment();
    if (name == null || name.length() == 0) name = "file";
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf(':'));
    if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
    return name;
  }

  private static String text(TextView view) {
    if (view == null || view.getText() == null) return "";
    return view.getText().toString();
  }

  private static void write(File file, byte[] bytes) throws Exception {
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(bytes);
    } finally {
      out.close();
    }
  }

  private static byte[] readAll(File file) throws Exception {
    FileInputStream in = new FileInputStream(file);
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[65536];
      int n;
      while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
      return bos.toByteArray();
    } finally {
      in.close();
    }
  }

  private static int dp(Activity activity, int n) {
    return Math.round(n * activity.getResources().getDisplayMetrics().density);
  }
}
