package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Map;

/** DrumMidi switches, one text field each, saved per program. */
public final class PyJavParams {
  private static final String[] FLAGS = DrumMidiArgs.FLAGS;
  private static final String[] LABELS = DrumMidiArgs.LABELS;
  private static final String[] HINTS = DrumMidiArgs.HINTS;
  private static final String[] SUGGESTED = DrumMidiArgs.SUGGESTED;
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
    LinearLayout resets = new LinearLayout(activity);
    resets.setOrientation(LinearLayout.HORIZONTAL);
    android.widget.Button defaults = new android.widget.Button(activity);
    defaults.setText("Reset to defaults");
    defaults.setTag("params-defaults");
    android.widget.Button suggested = new android.widget.Button(activity);
    suggested.setText("Reset to suggested values");
    suggested.setTag("params-suggested");
    resets.addView(defaults, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    resets.addView(suggested, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    box.addView(resets);
    TextView note = new TextView(activity);
    note.setText(DrumMidiArgs.NOTE);
    note.setTextSize(12);
    note.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
    box.addView(note);
    defaults.setOnClickListener(v -> {
      for (EditText f : fields) f.setText("");
    });
    suggested.setOnClickListener(v -> {
      for (int i = 0; i < fields.length; i++) fields[i].setText(SUGGESTED[i]);
    });
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
            String[] values = new String[FLAGS.length];
            for (int i = 0; i < FLAGS.length; i++) values[i] = fields[i].getText() == null ? "" : fields[i].getText().toString();
            String args = DrumMidiArgs.build(values);
            activity.getSharedPreferences(PREFS, 0).edit().putString(name, args).apply();
            try {
              if (activity instanceof MainActivity) ((MainActivity) activity).pyJav.pkSetPyArgs(args);
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
    return DrumMidiArgs.merge(extra, saved);
  }

  private static Map<String, String> read(String extra) {
    return DrumMidiArgs.read(extra);
  }

  private static int dp(Activity activity, int n) {
    return Math.round(n * activity.getResources().getDisplayMetrics().density);
  }
}
