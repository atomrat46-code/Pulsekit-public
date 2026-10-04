package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * PyJav's Params: the loaded program's parameters, read from its Usage line or argparse calls.
 * A .wav, .mp3, .mid or .txt parameter gets a file button; anything else a text field. Switches are
 * saved per program; chosen files go into the arguments.
 */
public final class PyJavParams {
  private static final String PREFS = "pulsekit-pyjav-params";
  static final int PICK_FILE = 30;

  /** The file row waiting for the picker. */
  private static String[] pendingValues;
  private static int pendingIndex = -1;
  private static TextView pendingLabel;

  private PyJavParams() {}

  /** Params for the DrumMidi switches, as before programs listed their own. */
  public static void open(final Activity activity, final String program, String extra) {
    open(activity, program, extra, "Usage: java DrumMidi <input.wav> <output.mid> [--sens N] [--hat N] [--tom N] [--ride N] "
        + "[--crash N] [--bpm N] [--quantize N] [--no-hpss]");
  }

  public static void open(final Activity activity, final String program, final String extra, String programText) {
    final String name = program == null || program.length() == 0 ? "program" : program;
    final List<ProgramParams.Param> ps = ProgramParams.parse(programText);
    if (ps.isEmpty()) {
      new AlertDialog.Builder(activity)
          .setTitle("Parameters · " + name)
          .setMessage("No parameters found in " + name + ". A line such as\nUsage: java Prog <input.wav> [song.mid] [--level N]\n"
              + "in the program lists them here.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    final String[] values = ProgramParams.values(ps, load(activity, name), extra);
    final String[] before = values.clone();
    if (activity instanceof MainActivity) {
      String input = ((MainActivity) activity).pyJav.pkPyInputPath;
      for (int i = 0; i < ps.size(); i++) {
        ProgramParams.Param p = ps.get(i);
        if (p.flag || p.output) continue;
        if (values[i].length() == 0 && input != null && ProgramParams.isAudio(p) && input.toLowerCase().matches(".*\\.(wav|wave|mp3)$")) values[i] = input;
        break;
      }
    }
    final EditText[] fields = new EditText[ps.size()];
    int pad = dp(activity, 12);
    LinearLayout box = new LinearLayout(activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(pad, pad, pad, pad);
    LinearLayout resets = new LinearLayout(activity);
    resets.setOrientation(LinearLayout.HORIZONTAL);
    android.widget.Button defaults = new android.widget.Button(activity);
    defaults.setText("Reset to defaults");
    defaults.setTag("params-defaults");
    resets.addView(defaults, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    if (ProgramParams.hasSuggested(ps)) {
      android.widget.Button suggested = new android.widget.Button(activity);
      suggested.setText("Reset to suggested values");
      suggested.setTag("params-suggested");
      resets.addView(suggested, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      suggested.setOnClickListener(v -> {
        for (int i = 0; i < fields.length; i++) if (fields[i] != null) fields[i].setText(ps.get(i).suggested);
      });
    }
    box.addView(resets);
    TextView note = new TextView(activity);
    note.setText(ProgramParams.note(ps));
    note.setTextSize(12);
    note.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
    box.addView(note);
    defaults.setOnClickListener(v -> {
      for (EditText f : fields) if (f != null) f.setText("");
    });
    for (int i = 0; i < ps.size(); i++) {
      final ProgramParams.Param p = ps.get(i);
      final int index = i;
      TextView label = new TextView(activity);
      label.setText(p.flag ? p.label + "  " + p.token : p.label + (p.optional ? "  (optional)" : ""));
      label.setPadding(0, dp(activity, 8), 0, dp(activity, 2));
      box.addView(label);
      if (p.output && !p.optional) {
        TextView auto = new TextView(activity);
        auto.setText("Named by PyJav from the input file");
        auto.setTextSize(12);
        box.addView(auto);
        continue;
      }
      if (p.isFile()) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.Button pick = new android.widget.Button(activity);
        pick.setText("Choose ." + p.ext);
        pick.setTag("params-file:" + p.token);
        final TextView chosen = new TextView(activity);
        chosen.setTag("params-chosen:" + p.token);
        chosen.setPadding(dp(activity, 8), 0, 0, 0);
        chosen.setText(fileLabel(values[i]));
        pick.setOnClickListener(v -> pickFile(activity, values, index, chosen));
        row.addView(pick);
        row.addView(chosen, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row);
        if ("mid".equals(p.ext) && activity instanceof MainActivity) {
          // DrumMidi's MIDI is kept with the file set it made (source.mid), not as a file to browse to.
          android.widget.Button fromSet = new android.widget.Button(activity);
          fromSet.setText("From file set");
          fromSet.setTag("params-fileset:" + p.token);
          fromSet.setOnClickListener(v -> pickFileSetMidi((MainActivity) activity, values, index, chosen));
          box.addView(fromSet, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        continue;
      }
      EditText field = new EditText(activity);
      field.setSingleLine(true);
      field.setInputType(InputType.TYPE_CLASS_TEXT);
      field.setHint(p.hint);
      field.setTag("params-field:" + p.token);
      field.setText(values[i]);
      fields[i] = field;
      if (p.choices != null && p.choices.length > 0) {
        // A list to pick from (SogniMusic's --genre: Pulsekit's style database); typing still works.
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(field, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Button choose = new android.widget.Button(activity);
        choose.setText("Choose");
        choose.setTag("params-choose:" + p.token);
        final EditText target = field;
        choose.setOnClickListener(v -> new AlertDialog.Builder(activity)
            .setTitle(p.label)
            .setItems(p.choices, (d, which) -> target.setText(p.choices[which]))
            .setNegativeButton("Cancel", null)
            .show());
        row.addView(choose);
        box.addView(row);
        continue;
      }
      box.addView(field);
    }
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(box);
    new AlertDialog.Builder(activity)
        .setTitle("Parameters · " + name)
        .setView(scroll)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
          @Override
          public void onClick(android.content.DialogInterface dialog, int which) {
            for (int i = 0; i < fields.length; i++) {
              if (fields[i] != null) values[i] = fields[i].getText() == null ? "" : fields[i].getText().toString();
            }
            String switches = ProgramParams.build(ps, values);
            activity.getSharedPreferences(PREFS, 0).edit().putString(name, switches).apply();
            boolean newInput = false;
            String input = null;
            for (int i = 0; i < ps.size(); i++) {
              ProgramParams.Param p = ps.get(i);
              if (p.flag || p.output) continue;
              input = values[i];
              newInput = !values[i].equals(before[i]);
              break;
            }
            String line = ProgramParams.line(ps, values, newInput ? "" : extra);
            try {
              if (activity instanceof MainActivity) ((MainActivity) activity).pyJav.pkSetPyLine(line, newInput ? input : null);
            } catch (Throwable ignored) {
              /* the switches are still saved for the next run */
            }
          }
        })
        .show();
  }

  static String fileLabel(String path) {
    if (path == null || path.length() == 0) return "None";
    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return slash >= 0 ? path.substring(slash + 1) : path;
  }

  /** Lists the file sets that keep a source MIDI; the one picked is copied into PyJav's input folder. */
  static void pickFileSetMidi(final MainActivity app, final String[] values, final int index, final TextView label) {
    final java.util.LinkedHashMap<String, String> sets = HitCompare.fileSetsWithMidi(Engine.fileSetSources(app.learned, app.learnedFills));
    if (sets.isEmpty()) {
      new AlertDialog.Builder(app)
          .setTitle("From file set")
          .setMessage("No file set keeps a source MIDI yet. Run DrumMidi_CRT in PyJav: its MIDI is kept with the file set it makes.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    final String[] labels = sets.keySet().toArray(new String[0]);
    new AlertDialog.Builder(app)
        .setTitle("Source MIDI from file set")
        .setItems(labels, (d, which) -> {
          try {
            byte[] midi = HitCompare.fileSetMidi(sets.get(labels[which]));
            java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, ProgramParams.fileSetMidiFile(labels[which]));
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            try {
              fos.write(midi);
            } finally {
              fos.close();
            }
            values[index] = out.getAbsolutePath();
            label.setText(labels[which] + " \u00b7 source.mid");
          } catch (Exception ex) {
            app.setNow("Could not copy that file set's MIDI");
          }
        })
        .show();
  }

  static void pickFile(Activity activity, String[] values, int index, TextView label) {
    pendingValues = values;
    pendingIndex = index;
    pendingLabel = label;
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    activity.startActivityForResult(intent, PICK_FILE);
  }

  /** The picker's file, copied into PyJav's input folder, for the waiting file row. */
  public static void filePicked(String path) {
    if (pendingValues == null || pendingIndex < 0 || pendingIndex >= pendingValues.length || path == null) return;
    pendingValues[pendingIndex] = path;
    if (pendingLabel != null) pendingLabel.setText(fileLabel(path));
    pendingValues = null;
    pendingIndex = -1;
    pendingLabel = null;
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

  /** File paths stay. Saved switches replace the program's own switches. */
  public static String merge(String programText, String extra, String saved) {
    return ProgramParams.merge(ProgramParams.parse(programText), extra, saved);
  }

  private static int dp(Activity activity, int n) {
    return Math.round(n * activity.getResources().getDisplayMetrics().density);
  }
}
