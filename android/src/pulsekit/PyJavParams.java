package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;

/** DrumMidi switches, one text field each, saved per program. */
public final class PyJavParams {
  private static final String[] FLAGS = {
    "--sens", "--hat", "--tom", "--ride", "--crash", "--bpm", "--quantize", "--no-hpss"
  };
  private static final String[] LABELS = {
    "Sensitivity", "Hi-hat", "Toms", "Ride", "Crash", "BPM", "Quantize", "No HPSS"
  };
  private static final String[] HINTS = {
    "1.0", "1.0", "1.0", "1.0", "1.0", "auto", "off", "1 to turn off"
  };
  private static final String PREFS = "pulsekit-pyjav-params";

  private PyJavParams() {}

  public static void open(final Activity activity, final String program, String extra) {
    final String name = program == null || program.length() == 0 ? "program" : program;
    final Map<String, String> saved = read(load(activity, name));
    final Map<String, String> current = read(extra);
    final EditText[] fields = new EditText[FLAGS.length];
    int pad = dp(activity, 12);
    LinearLayout box = new LinearLayout(activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(pad, pad, pad, pad);
    for (int i = 0; i < FLAGS.length; i++) {
      TextView label = new TextView(activity);
      label.setText(LABELS[i] + "  " + FLAGS[i]);
      label.setPadding(0, dp(activity, 8), 0, dp(activity, 2));
      EditText field = new EditText(activity);
      field.setSingleLine(true);
      field.setInputType(InputType.TYPE_CLASS_TEXT);
      field.setHint(HINTS[i]);
      String value = saved.containsKey(FLAGS[i]) ? saved.get(FLAGS[i]) : current.get(FLAGS[i]);
      if (value == null) value = "";
      field.setText(value);
      fields[i] = field;
      box.addView(label);
      box.addView(field);
    }
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(box);
    new AlertDialog.Builder(activity)
        .setTitle("DrumMidi parameters")
        .setView(scroll)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
          @Override
          public void onClick(android.content.DialogInterface dialog, int which) {
            StringBuilder built = new StringBuilder();
            for (int i = 0; i < FLAGS.length; i++) {
              String value = fields[i].getText() == null ? "" : fields[i].getText().toString().trim();
              if (value.length() == 0) continue;
              if ("--no-hpss".equals(FLAGS[i])) {
                if (on(value)) {
                  if (built.length() > 0) built.append(' ');
                  built.append(FLAGS[i]);
                }
                continue;
              }
              if (built.length() > 0) built.append(' ');
              built.append(FLAGS[i]).append(' ').append(value);
            }
            String args = built.toString();
            activity.getSharedPreferences(PREFS, 0).edit().putString(name, args).apply();
            try {
              activity.getClass().getMethod("pkSetPyArgs", String.class).invoke(activity, args);
            } catch (Throwable ignored) {
              /* the field is still saved for the next run */
            }
          }
        })
        .show();
  }

  public static String load(Activity activity, String program) {
    if (activity == null || program == null) return null;
    SharedPreferences prefs = activity.getSharedPreferences(PREFS, 0);
    if (!prefs.contains(program)) return null;
    String args = prefs.getString(program, "");
    return args == null ? "" : args;
  }

  /** File paths stay. A saved string replaces DrumMidi flags. Null means nothing was saved. */
  public static String merge(String extra, String saved) {
    if (saved == null) return extra == null ? "" : extra.trim();
    String kept = strip(extra);
    if (saved.trim().length() == 0) return kept;
    if (kept.length() == 0) return saved.trim();
    return kept + " " + saved.trim();
  }

  private static String strip(String extra) {
    if (extra == null || extra.trim().length() == 0) return "";
    String[] parts = extra.trim().split("\\s+");
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      int flag = index(parts[i]);
      if (flag >= 0) {
        if (!"--no-hpss".equals(FLAGS[flag]) && i + 1 < parts.length && !parts[i + 1].startsWith("-")) i++;
        continue;
      }
      if (out.length() > 0) out.append(' ');
      out.append(parts[i]);
    }
    return out.toString();
  }

  private static Map<String, String> read(String extra) {
    Map<String, String> out = new LinkedHashMap<String, String>();
    if (extra == null || extra.trim().length() == 0) return out;
    String[] parts = extra.trim().split("\\s+");
    for (int i = 0; i < parts.length; i++) {
      int flag = index(parts[i]);
      if (flag < 0) continue;
      if ("--no-hpss".equals(FLAGS[flag])) {
        out.put(FLAGS[flag], "1");
        continue;
      }
      if (i + 1 < parts.length && !parts[i + 1].startsWith("-")) out.put(FLAGS[flag], parts[++i]);
    }
    return out;
  }

  private static int index(String token) {
    for (int i = 0; i < FLAGS.length; i++) if (FLAGS[i].equals(token)) return i;
    return -1;
  }

  private static boolean on(String value) {
    return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
  }

  private static int dp(Activity activity, int n) {
    return Math.round(n * activity.getResources().getDisplayMetrics().density);
  }
}
