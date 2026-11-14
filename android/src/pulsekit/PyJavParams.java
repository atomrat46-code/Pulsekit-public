package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
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
  static String[] pendingValues;
  private static int pendingIndex = -1;
  private static TextView pendingLabel;
  /** The folder picked is MediaBrowser's (opened on it), not a program's folder switch. */
  static boolean pendingOpensMedia = true;
  /** The prompt field waiting for the picker's text file (Select file under --prompt). */
  private static EditText pendingPrompt;

  private PyJavParams() {}

  /** Params for the DrumMidi switches, as before programs listed their own. */
  public static void open(final Activity activity, final String program, String extra) {
    open(activity, program, extra, "Usage: java DrumMidi <input.wav> <output.mid> [--sens N] [--hat N] [--tom N] [--ride N] "
        + "[--crash N] [--bpm N] [--quantize N] [--no-hpss] [--no-cymbals]");
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
    // A video picked for a picture row fills it (and an empty second picture row) with its frames.
    shownParams = ps;
    shownValues = values;
    shownLabels = new TextView[ps.size()];
    // Rows left out of the run (their Use box unticked) keep their files beside the Params.
    final java.util.Set<String> off = new java.util.LinkedHashSet<String>();
    java.util.Map<String, String> offValues = ProgramParams.offValues(ps, load(activity, name + ProgramParams.OFF_KEY));
    for (int i = 0; i < ps.size(); i++) {
      String kept = offValues.get(ps.get(i).token);
      if (kept == null || !ProgramParams.canTurnOff(ps.get(i))) continue;
      values[i] = kept;
      off.add(ps.get(i).token);
    }
    final android.widget.CheckBox[] uses = new android.widget.CheckBox[ps.size()];
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
    shownFields = fields;
    // On/off switches (DrumMidi's --no-hpss and --no-cymbals) are checkboxes.
    final android.widget.CheckBox[] checks = new android.widget.CheckBox[ps.size()];
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
        for (int i = 0; i < checks.length; i++) if (checks[i] != null) checks[i].setChecked(DrumMidiArgs.on(ps.get(i).suggested));
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
      for (android.widget.CheckBox c : checks) if (c != null) c.setChecked(false);
    });
    // Where each row's views start in the page (vst_render's rows then go onto its tabs).
    final int[] rowStarts = new int[ps.size()];
    for (int i = 0; i < ps.size(); i++) {
      final ProgramParams.Param p = ps.get(i);
      final int index = i;
      rowStarts[i] = box.getChildCount();
      if (p.flag && !p.takesValue) {
        android.widget.CheckBox check = new android.widget.CheckBox(activity);
        check.setText(p.label + "  " + p.token);
        check.setTag("params-check:" + p.token);
        check.setChecked(DrumMidiArgs.on(values[i]));
        check.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
        checks[i] = check;
        box.addView(check);
        continue;
      }
      TextView label = new TextView(activity);
      label.setText(p.flag ? p.label + "  " + p.token : p.label + (p.optional ? "  (optional)" : ""));
      label.setPadding(0, dp(activity, 8), 0, dp(activity, 2));
      if (ParamsClip.has(name, p)) {
        // The prompt's and the LoRAs' Copy, Paste and Send to another Sogni program, after the label.
        LinearLayout withClip = new LinearLayout(activity);
        withClip.setOrientation(LinearLayout.HORIZONTAL);
        withClip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        withClip.addView(label);
        for (View b : clipButtons(activity, name, p.token, () -> fields[index])) withClip.addView(b);
        box.addView(withClip);
      } else {
        box.addView(label);
      }
      if (p.output && !p.optional) {
        TextView auto = new TextView(activity);
        auto.setText("Named by PyJav from the input file");
        auto.setTextSize(12);
        box.addView(auto);
        continue;
      }
      if (p.dir && p.typed) {
        // A folder as a path to write on (vst_render's --zip_outdir): Browse puts the picked folder in
        // the field; % codes and the like can then be added to it.
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final android.widget.EditText path = new android.widget.EditText(activity);
        path.setTag("params-dir-text:" + p.token);
        path.setSingleLine(true);
        path.setHint(p.hint);
        path.setText(values[i]);
        path.addTextChangedListener(new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {}

          public void afterTextChanged(android.text.Editable s) {
            values[index] = s.toString().trim();
          }
        });
        android.widget.Button browse = new android.widget.Button(activity);
        browse.setText("Browse");
        browse.setTag("params-dir:" + p.token);
        browse.setOnClickListener(v -> {
          pendingPrompt = null;
          pendingValues = values;
          pendingIndex = index;
          pendingLabel = path;
          pendingOpensMedia = false;
          if (activity instanceof MainActivity) MediaBrowser.pick((MainActivity) activity);
        });
        android.widget.Button clear = new android.widget.Button(activity);
        clear.setText("Clear");
        clear.setTag("params-clear:" + p.token);
        clear.setOnClickListener(v -> path.setText(""));
        row.addView(browse);
        row.addView(clear);
        useBox(activity, p, i, row, path, uses, off);
        spaced(activity, row);
        box.addView(row);
        box.addView(path);
        continue;
      }
      if (p.dir) {
        // A folder (MediaBrowser's directory): Browse picks it with the system's picker and opens the Media browser on it.
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.Button browse = new android.widget.Button(activity);
        browse.setText("Browse");
        browse.setTag("params-dir:" + p.token);
        // Empty: the folder the Media browser last opened.
        // (A folder switch, as Midi2HydroPattern's --dir, stays empty until one is picked.)
        if (values[i].length() == 0 && !p.flag && activity instanceof MainActivity) {
          MediaBrowser.load((MainActivity) activity);
          values[i] = MediaDir.lastRoot;
        }
        final TextView chosen = new TextView(activity);
        chosen.setTag("params-chosen:" + p.token);
        chosen.setPadding(dp(activity, 8), 0, 0, 0);
        chosen.setText(MediaDir.label(values[i]));
        browse.setOnClickListener(v -> {
          pendingPrompt = null;
          pendingValues = values;
          pendingIndex = index;
          pendingLabel = chosen;
          pendingOpensMedia = !p.flag;
          if (activity instanceof MainActivity) MediaBrowser.pick((MainActivity) activity);
        });
        row.addView(browse);
        // Download: Android lets no app pick it with Browse; the Media browser reads it through Android's own list.
        if (!p.flag && activity instanceof MainActivity) {
          android.widget.Button download = new android.widget.Button(activity);
          download.setText("Download");
          download.setTag("params-download:" + p.token);
          download.setOnClickListener(v -> {
            values[index] = MediaBrowser.DOWNLOADS;
            chosen.setText(MediaDir.label(values[index]));
            MediaBrowser.open((MainActivity) activity, MediaBrowser.DOWNLOADS);
          });
          row.addView(download);
        }
        if (p.pls) row.addView(playlistButton(activity, p, values, index, chosen));
        if (p.flag) {
          // Clear: a folder switch (CutWav's and SplitWav's --dir) goes back to unset, so one file is processed.
          android.widget.Button clear = new android.widget.Button(activity);
          clear.setText("Clear");
          clear.setTag("params-clear:" + p.token);
          clear.setOnClickListener(v -> {
            values[index] = "";
            chosen.setText(MediaDir.label(values[index]));
          });
          row.addView(clear);
        }
        useBox(activity, p, i, row, chosen, uses, off);
        spaced(activity, row);
        box.addView(row);
        box.addView(nameLine(activity, chosen));
        continue;
      }
      if (p.isFile()) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.Button pick = new android.widget.Button(activity);
        pick.setText("any".equals(p.ext) ? "Choose file" : "Choose ." + p.ext);
        pick.setTag("params-file:" + p.token);
        final TextView chosen = new TextView(activity);
        chosen.setTag("params-chosen:" + p.token);
        chosen.setPadding(dp(activity, 8), 0, 0, 0);
        chosen.setText(fileLabel(values[i]));
        shownLabels[i] = chosen;
        // Each row's Choose file opens where its own last file was (Reference image 1 and 2 apart).
        final String rowKey = p.token;
        if (ProgramParams.previewsPicture(p)) pictureRows.add(rowKey);
        else pictureRows.remove(rowKey);
        pick.setOnClickListener(v -> pickFile(activity, values, index, chosen, rowKey));
        row.addView(pick);
        if (p.refs) {
          // A picture can also come from the prompt library's reference files; greyed when it has none.
          android.widget.Button db = new android.widget.Button(activity);
          db.setText("Browse DB");
          db.setTag("params-db:" + p.token);
          // An audio row (DrumMidi's input) lists only sound files, a MIDI row (CompareHits) only MIDI files.
          // DrumMidi's input also lists MIDI files: one is played with the kit to a WAV for it.
          final boolean midiAsAudio = p.midiAsAudio;
          final String only = midiAsAudio ? RefBrowser.SOUNDS_OR_MIDIS
              : p.join || "mp4".equals(p.ext) ? RefBrowser.VIDEOS
              : ProgramParams.isAudio(p) ? RefBrowser.SOUNDS : "mid".equals(p.ext) ? RefBrowser.MIDIS : null;
          db.setEnabled(!RefBrowser.allFiles(activity, only).isEmpty());
          db.setOnClickListener(v -> {
            if (midiAsAudio) RefBrowser.browse(activity, only, (picked, file) -> RefBrowser.useAsAudio(activity, picked, file, values, index, chosen));
            else RefBrowser.browse(activity, values, index, chosen, only);
          });
          row.addView(db);
        }
        if (p.pls) row.addView(playlistButton(activity, p, values, index, chosen));
        if (p.newChat) {
          // SogniChat: New chat leaves the saved chat out, so the next run starts afresh.
          android.widget.Button fresh = new android.widget.Button(activity);
          fresh.setText("New chat");
          fresh.setTag("params-newchat:" + p.token);
          fresh.setOnClickListener(v -> {
            values[index] = "";
            chosen.setText(fileLabel(""));
          });
          row.addView(fresh);
        } else {
          // Clear: a file picked by mistake leaves the row (a second reference image, say).
          android.widget.Button clear = new android.widget.Button(activity);
          clear.setText("Clear");
          clear.setTag("params-clear:" + p.token);
          clear.setOnClickListener(v -> {
            values[index] = "";
            chosen.setText(fileLabel(""));
          });
          row.addView(clear);
        }
        if (p.join) {
          // SogniVideo's Join with this video: the file buttons work only when it is ticked; unticked
          // leaves the video out. PyJav unticks it after every run.
          final android.widget.CheckBox join = new android.widget.CheckBox(activity);
          join.setText("Join with this video");
          join.setTag("params-join:" + p.token);
          join.setChecked(values[i].length() > 0);
          final java.util.List<android.view.View> buttons = new java.util.ArrayList<android.view.View>();
          for (int c = 0; c < row.getChildCount(); c++) if (row.getChildAt(c) instanceof android.widget.Button) buttons.add(row.getChildAt(c));
          final boolean[] dbAny = new boolean[buttons.size()];
          for (int c = 0; c < buttons.size(); c++) dbAny[c] = buttons.get(c).isEnabled();
          final Runnable paint = () -> {
            for (int c = 0; c < buttons.size(); c++) {
              buttons.get(c).setEnabled(join.isChecked() && dbAny[c]);
              buttons.get(c).setAlpha(join.isChecked() ? 1f : 0.4f);
            }
          };
          join.setOnCheckedChangeListener((b, on) -> {
            if (!on) {
              values[index] = "";
              chosen.setText(fileLabel(""));
            }
            paint.run();
          });
          paint.run();
          // The label above the row is the checkbox here.
          box.removeView(label);
          box.addView(join);
        }
        useBox(activity, p, i, row, chosen, uses, off);
        box.addView(spaced(activity, row));
        box.addView(nameLine(activity, chosen));
        if (ProgramParams.previewsPicture(p)) {
          // Preview: a thumbnail of the picked picture pops up; greyed while no picture is picked.
          final android.widget.Button preview = new android.widget.Button(activity);
          preview.setText("Preview");
          preview.setTag("params-preview:" + p.token);
          // Crop: the picture with the crop frame on; its long press has Use the crop in this row (upscale asked when needed).
          final android.widget.Button[] crop = new android.widget.Button[1];
          if (activity instanceof MainActivity) {
            crop[0] = new android.widget.Button(activity);
            crop[0].setText(CropBox.CROP_ROW);
            crop[0].setTag("params-crop:" + p.token);
            crop[0].setOnClickListener(v -> cropPicture((MainActivity) activity, values[index], (cropName, file) -> assign(activity, values, index, chosen, file.getAbsolutePath(), null)));
          }
          final Runnable paintPreview = () -> {
            boolean on = ProgramParams.isPicture(values[index]);
            preview.setEnabled(on);
            preview.setAlpha(on ? 1f : 0.4f);
            if (crop[0] != null) {
              crop[0].setEnabled(on);
              crop[0].setAlpha(on ? 1f : 0.4f);
            }
          };
          paintPreview.run();
          chosen.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence t, int a, int b, int c) {}
            public void onTextChanged(CharSequence t, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) { paintPreview.run(); }
          });
          preview.setOnClickListener(v -> previewPicture(activity, values[index]));
          // Recent: the reference pictures used last as small thumbnails, beside Preview.
          android.widget.Button recent = new android.widget.Button(activity);
          recent.setText(RecentRefs.RECENT);
          recent.setTag("params-recent:" + p.token);
          recent.setOnClickListener(v -> recentRefs(activity, values, index, chosen));
          LinearLayout previewRow = new LinearLayout(activity);
          previewRow.setOrientation(LinearLayout.HORIZONTAL);
          previewRow.addView(preview);
          if (crop[0] != null) previewRow.addView(crop[0]);
          previewRow.addView(recent);
          box.addView(spaced(activity, previewRow), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
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
      if (p.dropdown && p.choices != null && p.choices.length > 0) {
        box.addView(dropdown(activity, p, values[i], fields, i));
        continue;
      }
      EditText field = new EditText(activity);
      if (ProgramParams.longText(p)) {
        // A prompt or system text: wrapped over several lines, scrolling inside the field. The
        // dialog scrolls too, so a drag in the field is kept for the field while it can scroll.
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setSingleLine(false);
        field.setHorizontallyScrolling(false);
        field.setMinLines(2);
        field.setMaxLines(6);
        field.setVerticalScrollBarEnabled(true);
        // The field's own movement method scrolls it and keeps long-press selection (Select all,
        // Cut, Copy, Paste); a ScrollingMovementMethod would turn selection off.
        field.setLongClickable(true);
        field.setOnTouchListener((v, ev) -> {
          if (v.canScrollVertically(1) || v.canScrollVertically(-1)) v.getParent().requestDisallowInterceptTouchEvent(true);
          if ((ev.getAction() & android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_UP) v.getParent().requestDisallowInterceptTouchEvent(false);
          return false;
        });
      } else {
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
      }
      field.setHint(p.hint);
      field.setTag("params-field:" + p.token);
      field.setText(values[i]);
      fields[i] = field;
      if (p.importList) {
        // A list row with Import (vst_render's Kits left out): a text file's lines into it.
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(field, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Button load = new android.widget.Button(activity);
        load.setText("Import");
        load.setTag("params-import:" + p.token);
        final EditText target = field;
        load.setOnClickListener(v -> pickPromptFile(activity, target));
        row.addView(load);
        box.addView(row);
        continue;
      }
      if (ProgramParams.stylesFromFile(p)) {
        // SogniMusic's styles list from a text file: Select file or Browse DB (one style a line, or with | or , between).
        box.addView(field);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final EditText target = field;
        android.widget.Button select = new android.widget.Button(activity);
        select.setText("Select file");
        select.setTag("params-styles-file:" + p.token);
        select.setOnClickListener(v -> pickPromptFile(activity, target));
        row.addView(select);
        android.widget.Button db = new android.widget.Button(activity);
        db.setText("Browse DB");
        db.setTag("params-styles-db:" + p.token);
        db.setEnabled(!RefBrowser.allFiles(activity, RefBrowser.TEXTS).isEmpty());
        db.setOnClickListener(v -> {
          DbFilter.current = "T";
          RefBrowser.browse(activity, RefBrowser.TEXTS, (picked, file) -> promptFrom(activity, target, file));
        });
        row.addView(db);
        box.addView(spaced(activity, row));
        continue;
      }
      if (ProgramParams.promptFromFile(p)) {
        // The prompt from a text file (an Answer Prompt 1.txt made by Extract prompt): Select file or Browse DB.
        box.addView(field);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final EditText target = field;
        android.widget.Button select = new android.widget.Button(activity);
        select.setText("Select file");
        select.setTag("params-prompt-file:" + p.token);
        select.setOnClickListener(v -> pickPromptFile(activity, target));
        row.addView(select);
        android.widget.Button db = new android.widget.Button(activity);
        db.setText("Browse DB");
        db.setTag("params-prompt-db:" + p.token);
        db.setEnabled(!RefBrowser.allFiles(activity, RefBrowser.TEXTS).isEmpty());
        db.setOnClickListener(v -> {
          // A prompt is a text file: Browse DB starts on T.
          DbFilter.current = "T";
          RefBrowser.browse(activity, RefBrowser.TEXTS, (picked, file) -> promptFrom(activity, target, file));
        });
        row.addView(db);
        // Browse prompts: a prompt from the prompt library, by category.
        android.widget.Button prompts = new android.widget.Button(activity);
        prompts.setText("Browse prompts");
        prompts.setTag("params-prompt-browse:" + p.token);
        prompts.setOnClickListener(v -> PromptBrowser.show(activity, (title, text) -> {
          target.setText(text);
          target.setSelection(0);
        }));
        row.addView(prompts);
        box.addView(spaced(activity, row));
        // Recent prompts: the six used last with this program, one picked into the field.
        android.widget.Button recent = new android.widget.Button(activity);
        recent.setText(PromptHistory.RECENT + " \u25be");
        recent.setTag("params-prompt-recent:" + p.token);
        recent.setOnClickListener(v -> {
          final java.util.List<String> got = PromptHistory.recent(activity.getFilesDir(), name);
          if (got.isEmpty()) {
            android.widget.Toast.makeText(activity, "No prompts run with " + name + " yet", android.widget.Toast.LENGTH_SHORT).show();
            return;
          }
          String[] rows = new String[got.size()];
          for (int k = 0; k < rows.length; k++) rows[k] = PromptHistory.label(got.get(k));
          lastRecent = new AlertDialog.Builder(activity).setTitle(PromptHistory.RECENT + " \u00b7 " + name).setItems(rows, (d, w) -> {
            target.setText(got.get(w));
            target.setSelection(0);
          }).setNegativeButton("Cancel", null).show();
        });
        if (SogniSections.shown(name)) {
          // Browse sections: the program's saved Params (Save at the bottom), one picked into the rows.
          LinearLayout more = new LinearLayout(activity);
          more.setOrientation(LinearLayout.HORIZONTAL);
          more.addView(recent);
          android.widget.Button sections = new android.widget.Button(activity);
          sections.setText(SogniSections.BROWSE);
          sections.setTag("params-sections:" + p.token);
          sections.setOnClickListener(v -> browseSections(activity, SogniSections.app(name), ps, fields));
          more.addView(sections);
          box.addView(spaced(activity, more));
        } else {
          box.addView(recent);
        }
        continue;
      }
      if (p.choices != null && p.choices.length > 0) {
        // A list to pick from (SogniMusic's --genre: Pulsekit's style database); typing still works.
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(field, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Button choose = new android.widget.Button(activity);
        choose.setText("Choose");
        choose.setTag("params-choose:" + p.token);
        final EditText target = field;
        choose.setOnClickListener(v -> SearchList.show(activity, p.label, p.choices, -1, -1, null, at -> target.setText(p.choiceValue(at))));
        row.addView(choose);
        // Clear: shown while something is chosen or typed (SogniMusic's --workflow back to a new run).
        final android.widget.Button clearChoice = new android.widget.Button(activity);
        clearChoice.setText("Clear");
        clearChoice.setTag("params-clear:" + p.token);
        clearChoice.setOnClickListener(v -> target.setText(""));
        clearChoice.setVisibility(field.getText().length() > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        field.addTextChangedListener(new android.text.TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void onTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void afterTextChanged(android.text.Editable s) {
            clearChoice.setVisibility(s.length() > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
          }
        });
        row.addView(clearChoice);
        box.addView(row);
        if (p.modelNote) {
          // SogniChat: image analyze mode (a DeepSeek vision model) or tools mode, as the model is picked.
          final TextView modeNote = new TextView(activity);
          modeNote.setTag("params-note:" + p.token);
          modeNote.setTextSize(12);
          modeNote.setPadding(dp(activity, 4), 0, 0, dp(activity, 6));
          modeNote.setText(ProgramParams.chatModelNote(field.getText().toString()));
          field.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
              modeNote.setText(ProgramParams.chatModelNote(s.toString()));
            }
          });
          box.addView(modeNote);
        }
        continue;
      }
      box.addView(field);
    }
    // Rows shown only when other rows are so (vst_render's Model A's number of kits when Model A is "the first N kits").
    final java.util.function.IntFunction<String> valueOf = j -> checks[j] != null ? (checks[j].isChecked() ? "on" : "") : fields[j] != null ? fields[j].getText().toString() : "";
    final java.util.Map<android.view.View, ProgramParams.Param> conds = new java.util.HashMap<android.view.View, ProgramParams.Param>();
    for (int i = 0; i < ps.size(); i++) {
      if (ps.get(i).showWhen == null) continue;
      int end = i + 1 < ps.size() ? rowStarts[i + 1] : box.getChildCount();
      for (int k = rowStarts[i]; k < end; k++) conds.put(box.getChildAt(k), ps.get(i));
    }
    rowShown = v -> {
      ProgramParams.Param c = conds.get(v);
      return c == null || ProgramParams.shown(c, ps, valueOf);
    };
    refreshRows = () -> {
      for (java.util.Map.Entry<android.view.View, ProgramParams.Param> e : conds.entrySet()) {
        boolean tabOk = tabOf.get(e.getKey()) == null || tabOf.get(e.getKey()).equals(paramsTab);
        e.getKey().setVisibility(tabOk && ProgramParams.shown(e.getValue(), ps, valueOf) ? android.view.View.VISIBLE : android.view.View.GONE);
      }
    };
    tabOf.clear();
    onTabs(activity, box, rowStarts, ps, values, uses);
    if (!conds.isEmpty()) {
      android.text.TextWatcher again = new android.text.TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence t, int a, int b, int c) {}

        @Override
        public void onTextChanged(CharSequence t, int a, int b, int c) {}

        @Override
        public void afterTextChanged(android.text.Editable t) {
          refreshRows.run();
        }
      };
      for (EditText f : fields) if (f != null) f.addTextChangedListener(again);
      for (int i = 0; i < checks.length; i++) if (checks[i] != null && ps.get(i).clears == null) checks[i].setOnCheckedChangeListener((b, on) -> refreshRows.run());
      refreshRows.run();
    }
    if (SogniSections.shown(name)) {
      // Export (Save is the window's button): the rows Save keeps, written into a file picked.
      android.widget.Button export = new android.widget.Button(activity);
      export.setText(ParamsClip.EXPORT);
      export.setTag("params-export-section");
      export.setAllCaps(false);
      export.setOnClickListener(v -> exportSection(activity, SogniSections.app(name), ps, fields));
      box.addView(export);
    }
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(box);
    shownFor = name;
    final android.content.DialogInterface.OnClickListener save = new android.content.DialogInterface.OnClickListener() {
      @Override
      public void onClick(android.content.DialogInterface dialog, int which) {
        for (int i = 0; i < fields.length; i++) {
          if (fields[i] != null) values[i] = fields[i].getText() == null ? "" : fields[i].getText().toString();
          // A several-line field is one argument: its lines joined.
          if (fields[i] != null && ProgramParams.longText(ps.get(i))) values[i] = ProgramParams.oneLine(values[i]);
          if (checks[i] != null) values[i] = checks[i].isChecked() ? "1" : "";
        }
        // The rows left out: kept beside the Params, not in them (a run takes the Params' switches).
        off.clear();
        for (int i = 0; i < uses.length; i++) if (uses[i] != null && !uses[i].isChecked()) off.add(ps.get(i).token);
        store(activity, name + ProgramParams.OFF_KEY, ProgramParams.build(ps, ProgramParams.onlyOff(ps, values, off)));
        System.arraycopy(ProgramParams.withoutOff(ps, values, off), 0, values, 0, values.length);
        String switches = ProgramParams.build(ps, values);
        // The prompt kept among the program's six last (Recent prompts).
        for (int i = 0; i < ps.size(); i++) if (ProgramParams.promptFromFile(ps.get(i))) PromptHistory.add(activity.getFilesDir(), name, values[i]);
        activity.getSharedPreferences(PREFS, 0).edit().putString(name, switches).apply();
        boolean newInput = false;
        String input = null;
        for (int i = 0; i < ps.size(); i++) {
          ProgramParams.Param p = ps.get(i);
          if (p.flag || p.output) continue;
          // A folder is not PyJav's input file.
          input = p.dir ? null : values[i];
          newInput = !values[i].equals(before[i]);
          break;
        }
        String line = ProgramParams.line(ps, values, newInput ? "" : extra);
        try {
          // Only into the program it was opened for: another loaded since (ImageUpscaler from the
          // Media browser that Choose file opened) was given SogniPedit's --prompt.
          if (activity instanceof MainActivity && name.equals(((MainActivity) activity).pyName)) ((MainActivity) activity).pyJav.pkSetPyLine(line, newInput ? input : null);
        } catch (Throwable ignored) {
          /* the switches are still saved for the next run */
        }
      }
    };
    // Instrument sessions: ticking one unticks the others, unless Multi-instrument session is ticked.
    for (int i = 0; i < checks.length; i++) {
      if (checks[i] == null || ps.get(i).clears == null) continue;
      final int at = i;
      checks[i].setOnCheckedChangeListener((b, on) -> {
        if (!on) return;
        boolean[] now = new boolean[checks.length];
        for (int k = 0; k < checks.length; k++) now[k] = checks[k] != null && checks[k].isChecked();
        for (int k : ProgramParams.toClear(ps, at, now)) checks[k].setChecked(false);
      });
    }
    AlertDialog.Builder built = new AlertDialog.Builder(activity)
        .setTitle("Parameters · " + name)
        .setView(scroll)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("OK", save);
    // Save (bottom left): the text rows kept as one of the program's sections; the Params stay open.
    if (SogniSections.shown(name)) built.setNeutralButton(SogniSections.SAVE, null);
    shown = built
        // Back (or a tap outside) closes it as OK does, what was picked kept; Cancel leaves it all as it was.
        .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
          @Override
          public void onCancel(android.content.DialogInterface dialog) {
            save.onClick(dialog, android.content.DialogInterface.BUTTON_POSITIVE);
          }
        })
        .show();
    android.widget.Button saveSection = shown.getButton(AlertDialog.BUTTON_NEUTRAL);
    if (saveSection != null && SogniSections.shown(name)) saveSection.setOnClickListener(v -> saveSection(activity, SogniSections.app(name), ps, fields));
  }

  /** The tests: Export's file (else the system's save dialog), Paste's answer (true: replace), Send's program and answer. */
  static java.io.File exportTo;
  static Boolean pasteReplace;
  static String sendTo;
  static Boolean sendYes;
  static volatile String clipSaid = "";
  static AlertDialog lastClip;

  static void clipSay(Activity activity, String text) {
    clipSaid = text;
    android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_SHORT).show();
  }

  /** Export: the rows Save keeps, written into a file picked ("Prompt: ...", "Loras: ..."). */
  static void exportSection(Activity activity, String program, List<ProgramParams.Param> ps, EditText[] fields) {
    java.util.Map<String, String> now = sectionFields(ps, fields);
    if (now.isEmpty()) {
      clipSay(activity, "Nothing to export: the prompt and the other rows are empty");
      return;
    }
    String text = ParamsClip.exportText(now);
    if (exportTo != null) {
      try {
        java.io.FileOutputStream out = new java.io.FileOutputStream(exportTo);
        try {
          out.write(text.getBytes("UTF-8"));
        } finally {
          out.close();
        }
        clipSay(activity, "Exported the " + program + " section into " + exportTo.getName());
      } catch (Exception ex) {
        clipSay(activity, "Could not export the section: " + ex.getMessage());
      }
      return;
    }
    SaveText.save((MainActivity) activity, ParamsClip.exportName(program, now), text);
  }

  /** The prompt's or LoRAs' Copy, Paste and Send buttons; `box` gives the row's field. */
  static List<View> clipButtons(final Activity activity, final String program, final String token, final java.util.function.Supplier<EditText> box) {
    final android.content.ClipboardManager clip = (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
    android.widget.Button copy = clipButton(activity, ParamsClip.COPY, "params-copy:" + token);
    android.widget.Button paste = clipButton(activity, ParamsClip.PASTE, "params-paste:" + token);
    android.widget.Button send = clipButton(activity, ParamsClip.SEND, "params-send:" + token);
    copy.setOnClickListener(v -> {
      String text = box.get().getText().toString();
      if (text.trim().length() > 0) clip.setPrimaryClip(android.content.ClipData.newPlainText(ParamsClip.what(token), text));
      clipSay(activity, ParamsClip.copied(token, text));
    });
    paste.setOnClickListener(v -> {
      CharSequence got = clip.hasPrimaryClip() && clip.getPrimaryClip().getItemCount() > 0 ? clip.getPrimaryClip().getItemAt(0).coerceToText(activity) : null;
      if (got == null || got.toString().trim().isEmpty()) {
        clipSay(activity, "The clipboard has no text");
        return;
      }
      final String text = got.toString().trim();
      final EditText f = box.get();
      final Runnable put = () -> {
        f.setText(text);
        clipSay(activity, "Pasted into the " + ParamsClip.what(token));
      };
      if (f.getText().toString().trim().isEmpty()) put.run();
      else if (pasteReplace != null) {
        if (pasteReplace.booleanValue()) put.run();
      } else {
        lastClip = new AlertDialog.Builder(activity).setTitle("Paste").setMessage(ParamsClip.replaceAsk(token))
            .setPositiveButton("Replace", (d, w) -> put.run()).setNegativeButton("Cancel", null).show();
      }
    });
    send.setOnClickListener(v -> {
      final String text = box.get().getText().toString();
      if (text.trim().isEmpty()) {
        clipSay(activity, "No " + ParamsClip.what(token) + " to send");
        return;
      }
      final List<String> targets = ParamsClip.targets(program, token, s -> programTextOf(activity, s));
      if (targets.isEmpty()) {
        clipSay(activity, "No other Sogni program takes a " + ParamsClip.what(token));
        return;
      }
      final java.util.function.Consumer<String> go = target -> {
        final Runnable put = () -> {
          List<ProgramParams.Param> theirs = ProgramParams.parse(programTextOf(activity, target));
          store(activity, target, ParamsClip.put(theirs, load(activity, target), token, text));
          clipSay(activity, ParamsClip.sent(token, target));
        };
        if (sendYes != null) {
          if (sendYes.booleanValue()) put.run();
          return;
        }
        lastClip = new AlertDialog.Builder(activity).setTitle(ParamsClip.SEND_TITLE).setMessage(ParamsClip.sendAsk(token, target, text))
            .setPositiveButton("Send", (d, w) -> put.run()).setNegativeButton("Cancel", null).show();
      };
      if (sendTo != null) {
        if (targets.contains(sendTo)) go.accept(sendTo);
        else clipSay(activity, sendTo + " takes no " + ParamsClip.what(token));
        return;
      }
      String[] rows = new String[targets.size()];
      for (int i = 0; i < rows.length; i++) rows[i] = SogniSections.app(targets.get(i));
      lastClip = new AlertDialog.Builder(activity).setTitle(ParamsClip.SEND_TITLE).setItems(rows, (d, w) -> go.accept(targets.get(w)))
          .setNegativeButton("Cancel", null).show();
    });
    return java.util.Arrays.<View>asList(copy, paste, send);
  }

  static android.widget.Button clipButton(Activity activity, String symbol, String tag) {
    android.widget.Button b = new android.widget.Button(activity, null, android.R.attr.buttonStyleSmall);
    b.setText(symbol);
    b.setTag(tag);
    b.setAllCaps(false);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    lp.leftMargin = dp(activity, 4);
    b.setLayoutParams(lp);
    return b;
  }

  /** A program's text for its Params (its bundled source); null when it is not there. */
  static String programTextOf(Activity activity, String program) {
    try {
      return PyJavHints.programText(program, new String(((MainActivity) activity).programMenus.read("Java", program), "UTF-8"), null);
    } catch (Exception ex) {
      return null;
    }
  }

  /** What Save or Browse sections said last, and the list shown last, for the tests. */
  static volatile String sectionSaid = "";
  static AlertDialog lastSections;

  /** The section rows' values now. */
  static java.util.Map<String, String> sectionFields(List<ProgramParams.Param> ps, EditText[] fields) {
    String[] now = new String[ps.size()];
    for (int i = 0; i < now.length; i++) now[i] = fields[i] == null || fields[i].getText() == null ? "" : fields[i].getText().toString();
    return SogniSections.fields(ps, now);
  }

  static void sectionSay(Activity activity, String text) {
    sectionSaid = text;
    android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_LONG).show();
  }

  /** Save: the rows kept as the program's section, unless the same one is saved already. True when they are saved now. */
  static boolean saveSection(Activity activity, String program, List<ProgramParams.Param> ps, EditText[] fields) {
    java.util.Map<String, String> now = sectionFields(ps, fields);
    if (now.isEmpty()) {
      sectionSay(activity, "Nothing to save: the prompt and the other rows are empty");
      return false;
    }
    try {
      PromptVault vault = PromptVault.open(activity.getFilesDir());
      String text = SogniSections.text(now);
      PromptVault.SogniSection same = vault.findSogniSection(program, text);
      if (same != null) {
        sectionSay(activity, "This section is saved already: " + SogniSections.label(same));
        return true;
      }
      PromptVault.SogniSection sec = vault.addSogniSection(program, text);
      sectionSay(activity, "Saved a " + program + " section: " + SogniSections.label(sec));
      return true;
    } catch (Exception ex) {
      sectionSay(activity, "Could not save the section: " + ex.getMessage());
      return false;
    }
  }

  /** Browse sections: the program's saved sections; one picked fills the rows (the entries now saved first, when asked). */
  static void browseSections(final Activity activity, final String program, final List<ProgramParams.Param> ps, final EditText[] fields) {
    final List<PromptVault.SogniSection> all;
    try {
      all = PromptVault.open(activity.getFilesDir()).sogniSections(program);
    } catch (Exception ex) {
      sectionSay(activity, "Could not read the sections: " + ex.getMessage());
      return;
    }
    if (all.isEmpty()) {
      sectionSay(activity, "No " + program + " sections yet: Save keeps the entries as one");
      return;
    }
    // Grouped by the prompt's first sentences, A to Z: a group's long press (or tap) lists its sections.
    final List<SogniSections.Group> groups = SogniSections.groups(all);
    String[] rows = new String[groups.size()];
    for (int i = 0; i < rows.length; i++) rows[i] = SogniSections.groupLabel(groups.get(i));
    lastSections = new AlertDialog.Builder(activity).setTitle(SogniSections.BROWSE + " \u00b7 " + program)
        .setItems(rows, (d, w) -> {
          SogniSections.Group g = groups.get(w);
          if (g.sections.size() == 1) showSection(activity, program, ps, fields, g.sections.get(0));
          else groupSections(activity, program, ps, fields, g);
        })
        .setNeutralButton("Delete\u2026", (d, w) -> deleteSections(activity, program, all))
        .setNegativeButton("Cancel", null).show();
    // Held: a group's sections; a section of its own, Load, Edit section, Remove section.
    final AlertDialog list = lastSections;
    list.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
      list.dismiss();
      SogniSections.Group g = groups.get(position);
      if (g.sections.size() == 1) sectionMenu(activity, program, ps, fields, g.sections.get(0));
      else groupSections(activity, program, ps, fields, g);
      return true;
    });
  }

  /** A group's sections listed (for the tests). */
  static AlertDialog lastGroup, lastSectionShown;

  /** A group's sections, the newest first: a tap shows one fully; held, Load, Edit section, Remove section. */
  static void groupSections(final Activity activity, final String program, final List<ProgramParams.Param> ps, final EditText[] fields, final SogniSections.Group g) {
    String[] rows = new String[g.sections.size()];
    for (int i = 0; i < rows.length; i++) rows[i] = SogniSections.label(g.sections.get(i));
    lastGroup = new AlertDialog.Builder(activity).setTitle(g.lead)
        .setItems(rows, (d, w) -> showSection(activity, program, ps, fields, g.sections.get(w)))
        .setNeutralButton("Back", (d, w) -> browseSections(activity, program, ps, fields))
        .setNegativeButton("Cancel", null).show();
    final AlertDialog list = lastGroup;
    list.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
      list.dismiss();
      sectionMenu(activity, program, ps, fields, g.sections.get(position));
      return true;
    });
  }

  /** A section shown fully (its entries, a line each): Load, Edit or remove…, Close. */
  static void showSection(final Activity activity, final String program, final List<ProgramParams.Param> ps, final EditText[] fields, final PromptVault.SogniSection sec) {
    TextView text = new TextView(activity);
    text.setTag("section-full-text");
    text.setText(SogniSections.full(sec));
    text.setTextIsSelectable(true);
    text.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 8));
    android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
    scroll.addView(text);
    lastSectionShown = new AlertDialog.Builder(activity).setTitle(SogniSections.label(sec)).setView(scroll)
        .setPositiveButton(SogniSections.LOAD, (d, w) -> pickSection(activity, program, ps, fields, sec))
        .setNeutralButton("Edit or remove\u2026", (d, w) -> sectionMenu(activity, program, ps, fields, sec))
        .setNegativeButton("Close", null).show();
  }

  /** A section's long-press menu shown last, and Edit section's / Remove section's dialog (for the tests). */
  static AlertDialog lastSectionMenu, lastSectionEdit;

  static void sectionMenu(final Activity activity, final String program, final List<ProgramParams.Param> ps, final EditText[] fields, final PromptVault.SogniSection sec) {
    String[] items = {SogniSections.LOAD, SogniSections.EDIT, SogniSections.REMOVE};
    lastSectionMenu = new AlertDialog.Builder(activity).setTitle(SogniSections.label(sec)).setItems(items, (d, w) -> {
      if (w == 0) pickSection(activity, program, ps, fields, sec);
      else if (w == 1) editSection(activity, program, ps, fields, sec);
      else removeSection(activity, program, ps, fields, sec);
    }).setNegativeButton("Cancel", null).show();
  }

  /** Edit section: its entries, a line each ("Prompt: ...", "Loras: ..."); Save keeps them. */
  static void editSection(final Activity activity, final String program, final List<ProgramParams.Param> ps, final EditText[] fields, final PromptVault.SogniSection sec) {
    final EditText text = new EditText(activity);
    text.setTag("section-edit-text");
    text.setText(sec.text.trim());
    text.setMinLines(4);
    text.setGravity(android.view.Gravity.TOP);
    LinearLayout box = new LinearLayout(activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), 0);
    box.addView(text);
    lastSectionEdit = new AlertDialog.Builder(activity).setTitle(SogniSections.EDIT).setView(box)
        .setPositiveButton("Save", (d, w) -> {
          String now = text.getText().toString().trim() + "\n";
          String problem = SogniSections.editProblem(now);
          if (problem != null) {
            sectionSay(activity, "Not saved: " + problem);
            return;
          }
          try {
            PromptVault.open(activity.getFilesDir()).updateSogniSection(sec.id, now);
            sec.text = now;
            sectionSay(activity, "Saved: " + SogniSections.label(sec));
          } catch (Exception ex) {
            sectionSay(activity, "Could not save the section: " + ex.getMessage());
          }
          browseSections(activity, program, ps, fields);
        })
        .setNegativeButton("Cancel", (d, w) -> browseSections(activity, program, ps, fields)).show();
  }

  /** Remove section: asked first. */
  static void removeSection(final Activity activity, final String program, final List<ProgramParams.Param> ps, final EditText[] fields, final PromptVault.SogniSection sec) {
    lastSectionEdit = new AlertDialog.Builder(activity).setTitle(SogniSections.REMOVE).setMessage(SogniSections.removeAsk(sec))
        .setPositiveButton("Remove", (d, w) -> {
          try {
            PromptVault.open(activity.getFilesDir()).deleteSogniSection(sec.id);
            sectionSay(activity, "Removed: " + SogniSections.label(sec));
          } catch (Exception ex) {
            sectionSay(activity, "Could not remove it: " + ex.getMessage());
          }
        })
        .setNegativeButton("Cancel", null).show();
  }

  static void pickSection(final Activity activity, final String program, final List<ProgramParams.Param> ps, final EditText[] fields, final PromptVault.SogniSection sec) {
    java.util.Map<String, String> now = sectionFields(ps, fields);
    boolean saved;
    try {
      saved = now.isEmpty() || PromptVault.open(activity.getFilesDir()).findSogniSection(program, SogniSections.text(now)) != null;
    } catch (Exception ex) {
      saved = false;
    }
    if (saved) {
      loadSection(activity, ps, fields, sec);
      return;
    }
    lastSections = new AlertDialog.Builder(activity).setTitle(SogniSections.BROWSE).setMessage(SogniSections.ASK)
        .setPositiveButton(SogniSections.SAVE_FIRST, (d, w) -> {
          if (saveSection(activity, program, ps, fields)) loadSection(activity, ps, fields, sec);
        })
        .setNeutralButton(SogniSections.DONT_SAVE, (d, w) -> loadSection(activity, ps, fields, sec))
        .setNegativeButton("Cancel", null).show();
  }

  /** The section's entries into the rows; the kept rows it does not set emptied. */
  static void loadSection(Activity activity, List<ProgramParams.Param> ps, EditText[] fields, PromptVault.SogniSection sec) {
    java.util.Map<String, String> f = SogniSections.read(sec.text);
    for (int i = 0; i < ps.size() && i < fields.length; i++) {
      if (fields[i] == null || !SogniSections.kept(ps.get(i))) continue;
      String v = f.get(ps.get(i).token);
      fields[i].setText(v == null ? "" : v);
    }
    sectionSay(activity, "Loaded: " + SogniSections.label(sec));
  }

  static void deleteSections(final Activity activity, final String program, final List<PromptVault.SogniSection> all) {
    String[] rows = new String[all.size()];
    for (int i = 0; i < rows.length; i++) rows[i] = SogniSections.label(all.get(i));
    lastSections = new AlertDialog.Builder(activity).setTitle("Delete a " + program + " section").setItems(rows, (d, w) -> {
      try {
        PromptVault.open(activity.getFilesDir()).deleteSogniSection(all.get(w).id);
        sectionSay(activity, "Deleted: " + rows[w]);
      } catch (Exception ex) {
        sectionSay(activity, "Could not delete it: " + ex.getMessage());
      }
    }).setNegativeButton("Cancel", null).show();
  }

  /**
   * A file or folder switch's Use box, first in its row (ticked: the file is in the run; unticked:
   * it stays chosen, greyed, and the run leaves it out). Other rows are left as they are.
   */
  static void useBox(Activity activity, ProgramParams.Param p, int i, LinearLayout row, final TextView chosen, android.widget.CheckBox[] uses, java.util.Set<String> off) {
    if (!ProgramParams.canTurnOff(p)) return;
    final android.widget.CheckBox use = new android.widget.CheckBox(activity);
    use.setText("Use");
    use.setTag("params-use:" + p.token);
    use.setChecked(!off.contains(p.token));
    chosen.setAlpha(use.isChecked() ? 1f : 0.4f);
    use.setOnCheckedChangeListener((b, on) -> chosen.setAlpha(on ? 1f : 0.4f));
    uses[i] = use;
    row.addView(use, 0);
  }

  /** The Params dialog shown last, and the program it is for. */
  static AlertDialog shown;
  static String shownFor;

  /** Another program loaded: its Params (still open under the Media browser Choose file opened) close. */
  static void closeOthers(String program) {
    AlertDialog d = shown;
    if (d != null && d.isShowing() && shownFor != null && !shownFor.equals(program)) d.dismiss();
  }

  /** Recent prompts' list shown last, for the tests. */
  static AlertDialog lastRecent;

  /** The Preview shown last, for the tests. */
  static AlertDialog lastParamsPreview;

  /** A thumbnail of the picture at path, in a small dialog (the file's name as its title). */
  static void previewPicture(Activity activity, String path) {
    previewPicture(activity, path, null);
  }

  /**
   * Crop on a picture row: the picture in the Preview with the crop frame on; the crop's long press
   * has Use the crop in this row (one that needs upscaling is asked Upscale first / Use as it is / Cancel), Save as…, Upscale.
   */
  static void cropPicture(MainActivity app, String path, RefBrowser.Picked row) {
    previewPicture(app, path, row);
  }

  /** The crop's actions on a Params row: the picture's bytes, the row, the Preview closed when the row is filled. */
  static final class RowCrop implements CropLayer.Actions, CropLayer.Fills {
    final MainActivity app;
    final byte[] bytes;
    final RefBrowser.Picked row;
    AlertDialog dialog;

    RowCrop(MainActivity app, byte[] bytes, RefBrowser.Picked row) {
      this.app = app;
      this.bytes = bytes;
      this.row = row;
    }

    public android.graphics.Bitmap cut(CropBox at) throws Exception {
      return CropLayer.cutBytes(this.bytes, at);
    }

    public String save(String name, byte[] png) throws Exception {
      String where = ArtJava.publish(this.app, name, png);
      if (where == null) throw new java.io.IOException("Download takes no file named " + name);
      return "Saved the crop as " + where;
    }

    public void upscale(String name, byte[] png) {
      RefImages.upscale(this.app, name, png, this.dialog, null);
    }

    public void fill(String name, byte[] png) {
      RefImages.forRow(this.app, name, png, this.dialog, null, this.row);
    }
  }

  /** As above; with `row` (Crop on a picture row) the crop frame is on from the start and its crop can fill the row. */
  static void previewPicture(Activity activity, String path, RefBrowser.Picked row) {
    // Picked from the library and gone from the temp folder since: written again first.
    try {
      InputOrigins.restore(activity.getFilesDir(), path, PromptVault.open(activity.getFilesDir()));
    } catch (Exception ignored) {
      // said below when it cannot be read
    }
    android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    android.graphics.BitmapFactory.decodeFile(path, bounds);
    int target = dp(activity, 280);
    int sample = 1;
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2;
    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
    opts.inSampleSize = sample;
    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path, opts);
    AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle(fileLabel(path)).setPositiveButton("Close", null);
    if (bitmap == null) {
      dialog.setMessage(new java.io.File(path).isFile() ? "The picture could not be read." : "The picture is not there any more (the phone emptied Pulsekit's temp folder): pick it again.");
    } else {
      android.widget.ImageView image = new android.widget.ImageView(activity);
      image.setTag("params-preview-image");
      image.setAdjustViewBounds(true);
      image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
      image.setMaxHeight(target);
      image.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), 0);
      image.setImageBitmap(bitmap);
      dialog.setMessage(bounds.outWidth + " \u00d7 " + bounds.outHeight);
      if (row != null && activity instanceof MainActivity) {
        // Crop: the frame over the picture (cut from the file at its full size).
        byte[] bytes = null;
        try {
          java.io.FileInputStream in = new java.io.FileInputStream(path);
          try {
            java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) all.write(buf, 0, n);
            bytes = all.toByteArray();
          } finally {
            in.close();
          }
        } catch (Exception ex) {
          bytes = null;
        }
        if (bytes != null) {
          android.widget.FrameLayout holder = new android.widget.FrameLayout(activity);
          holder.addView(image, new android.widget.FrameLayout.LayoutParams(-1, -2));
          RowCrop actions = new RowCrop((MainActivity) activity, bytes, row);
          CropLayer layer = CropLayer.over(activity, holder, image, fileLabel(path), actions);
          dialog.setView(holder);
          lastParamsPreview = dialog.show();
          actions.dialog = lastParamsPreview;
          layer.start();
          return;
        }
      }
      dialog.setView(image);
      if (activity instanceof MainActivity) {
        // Upscale: ImageUpscaler with this picture, its Params open; Return opens this program's Params again.
        final MainActivity app = (MainActivity) activity;
        dialog.setNeutralButton(UPSCALE_PREVIEW, (d, w) -> upscalePreview(app, path));
      }
    }
    lastParamsPreview = dialog.show();
  }

  static final String UPSCALE_PREVIEW = "Upscale";

  /**
   * Preview's Upscale: the Params closed (what was picked kept), then ImageUpscaler with the picture as
   * its input, its Params open; Return to the program opens its Params again.
   */
  static void upscalePreview(final MainActivity app, String path) {
    final String program = app.pyName;
    // Cancelled (as Back does): what was picked is saved.
    AlertDialog d = shown;
    if (d != null && d.isShowing()) d.cancel();
    app.openKitView("py");
    app.programMenus.selectProgram("Java", "ImageUpscaler.java");
    app.pyJav.pkUseInputPath(path);
    if (program != null) app.pyJav.pkSetReturn("Return to " + program.replaceAll("\\.java$", ""), () -> {
      app.openKitView("py");
      app.programMenus.selectProgram("Java", program);
      app.pyJav.pkOpenParams();
    });
    app.pyJav.pkOpenParams();
  }

  /**
   * The file or folder picked, on its own line under the row's buttons: the whole width for it, one
   * line, a long name shortened in the middle (its start and its ending, ".png", stay).
   */
  static TextView nameLine(Activity activity, TextView chosen) {
    chosen.setSingleLine(true);
    chosen.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
    chosen.setPadding(dp(activity, 12), 0, dp(activity, 8), dp(activity, 4));
    chosen.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    return chosen;
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
        .setNegativeButton("Cancel", null).show();
  }

  /**
   * Choose file: the Media browser, on the Recent MB folder, Download or a favourite folder (its
   * tabs); the file tapped fills the row. "Other…" there opens the system's picker as before.
   */
  /** The Playlist list shown last (for the tests). */
  static AlertDialog lastPlaylists;

  /** Playlist: one of Pulsekit's playlists (.pls) for a batch program's folder or list; its files are staged for the run. */
  static android.widget.Button playlistButton(final Activity activity, ProgramParams.Param p, final String[] values, final int index, final TextView chosen) {
    android.widget.Button b = new android.widget.Button(activity);
    b.setText("Playlist");
    b.setTag("params-pls:" + p.token);
    b.setOnClickListener(v -> {
      final java.io.File dir = activity.getFilesDir();
      final List<String> names = Playlist.withFiles(dir);
      AlertDialog.Builder ask = new AlertDialog.Builder(activity).setTitle("Playlist").setNegativeButton("Cancel", null);
      if (names.isEmpty()) ask.setMessage("No playlists yet: Add to playlist on the thumbnails makes one");
      else ask.setItems(names.toArray(new String[0]), (d, w) -> {
        values[index] = Playlist.file(dir, names.get(w)).getAbsolutePath();
        chosen.setText(names.get(w) + Playlist.EXT);
      });
      lastPlaylists = ask.show();
    });
    return b;
  }

  static void pickFile(final Activity activity, final String[] values, final int index, final TextView label) {
    pickFile(activity, values, index, label, null);
  }

  /** The Params row whose system picker is open (its switch): the file picked there is its row's last. */
  static String pendingRow;

  /** The rows (switches) of the Params shown that take a picture: Choose file plays a video there, C fills the row. */
  static final java.util.Set<String> pictureRows = new java.util.HashSet<String>();

  /** As above, for the row `key` (its switch): it opens where that row's last file was. */
  static void pickFile(final Activity activity, final String[] values, final int index, final TextView label, final String key) {
    Runnable system = () -> {
      pendingPrompt = null;
      pendingValues = values;
      pendingIndex = index;
      pendingLabel = label;
      pendingRow = key;
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("*/*");
      startIn(activity, intent, key);
      activity.startActivityForResult(intent, PICK_FILE);
    };
    if (!(activity instanceof MainActivity)) {
      system.run();
      return;
    }
    RefBrowser.Picked put = (name, file) -> assign(activity, values, index, label, file.getAbsolutePath(), null);
    if (key != null && key.length() > 0) MediaBrowser.chooseFor((MainActivity) activity, key, put, system, pictureRows.contains(key));
    else MediaBrowser.choose((MainActivity) activity, put, system);
  }

  /** Recent's list shown last (for the tests). */
  static AlertDialog lastRecentRefs;

  /** Recent: the reference pictures used last, each a small thumbnail with its name; a tap puts it in the row. */
  static void recentRefs(final Activity activity, final String[] values, final int index, final TextView label) {
    final List<java.io.File> pics = RecentRefs.list(activity.getFilesDir());
    if (pics.isEmpty()) {
      android.widget.Toast.makeText(activity, "No recent reference pictures yet: the pictures picked here are listed", android.widget.Toast.LENGTH_LONG).show();
      return;
    }
    LinearLayout col = new LinearLayout(activity);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
    final int px = dp(activity, 56);
    final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    final AlertDialog[] shown = new AlertDialog[1];
    for (final java.io.File f : pics) {
      LinearLayout row = new LinearLayout(activity);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(android.view.Gravity.CENTER_VERTICAL);
      row.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
      row.setTag("params-recent-ref:" + f.getName());
      final android.widget.ImageView thumb = new android.widget.ImageView(activity);
      thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
      row.addView(thumb, new LinearLayout.LayoutParams(px, px));
      TextView name = new TextView(activity);
      name.setText(f.getName());
      name.setPadding(dp(activity, 10), 0, 0, 0);
      row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
      row.setOnClickListener(v -> {
        assign(activity, values, index, label, f.getAbsolutePath(), " \u00b7 recent");
        if (shown[0] != null) shown[0].dismiss();
      });
      col.addView(row);
      new Thread(() -> {
        // Small: decoded at about the thumbnail's size.
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= px && o.outHeight / (sample * 2) >= px) sample *= 2;
        o = new android.graphics.BitmapFactory.Options();
        o.inSampleSize = sample;
        final android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (b != null) main.post(() -> thumb.setImageBitmap(b));
      }, "pulsekit-recent-thumb").start();
    }
    android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
    scroll.addView(col);
    shown[0] = new AlertDialog.Builder(activity).setTitle("Recent reference pictures").setView(scroll).setNegativeButton("Cancel", null).show();
    lastRecentRefs = shown[0];
  }

  /** Select file for a prompt field (Params, the Prompts page): the picker's text file fills it. */
  static void pickPromptFile(Activity activity, EditText field) {
    pendingValues = null;
    pendingIndex = -1;
    pendingLabel = null;
    pendingPrompt = field;
    pendingRow = null;
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("text/*");
    startIn(activity, intent);
    activity.startActivityForResult(intent, PICK_FILE);
  }

  static final String PICK_PREFS = "pulsekit-select-file";

  /** The file Select file picked last: the next one opens in its folder (a subfolder of Download too). */
  static void pickedLast(Activity activity, android.net.Uri uri) {
    android.content.SharedPreferences.Editor e = activity.getSharedPreferences(PICK_PREFS, 0).edit().putString("last", uri.toString());
    // A Params row's own: its next system picker opens there.
    if (pendingRow != null && pendingRow.length() > 0) e.putString("last:" + pendingRow, uri.toString());
    pendingRow = null;
    e.apply();
  }

  /** The picker opened where the last file picked is (Android 8 on). */
  static void startIn(Activity activity, Intent intent) {
    startIn(activity, intent, null);
  }

  /** As above; for a Params row (`key`), where that row's last file is (else the last of any). */
  static void startIn(Activity activity, Intent intent, String key) {
    android.content.SharedPreferences prefs = activity.getSharedPreferences(PICK_PREFS, 0);
    String last = key == null ? "" : prefs.getString("last:" + key, "");
    if (last.length() == 0) last = prefs.getString("last", "");
    if (last.length() > 0 && android.os.Build.VERSION.SDK_INT >= 26) intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, android.net.Uri.parse(last));
  }

  /** A text file's contents into a prompt field (a prompt sheet gives its prompt). */
  static void promptFrom(Activity activity, EditText field, java.io.File file) {
    try {
      java.io.FileInputStream in = new java.io.FileInputStream(file);
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      try {
        byte[] buf = new byte[65536];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
      } finally {
        in.close();
      }
      String text = ProgramParams.promptText(out.toByteArray());
      if (text.length() == 0) {
        android.widget.Toast.makeText(activity, file.getName() + " has no text", android.widget.Toast.LENGTH_LONG).show();
        return;
      }
      // An Import row (vst_render's Kits left out): the file's names, comma-separated.
      if (String.valueOf(field.getTag()).startsWith("params-field:") && importRow(String.valueOf(field.getTag()).substring("params-field:".length()))) {
        String list = ProgramParams.importedList(new String(out.toByteArray(), "UTF-8"));
        field.setText(list);
        int n = list.isEmpty() ? 0 : list.split(",").length;
        android.widget.Toast.makeText(activity, n + (n == 1 ? " name" : " names") + " from " + file.getName(), android.widget.Toast.LENGTH_SHORT).show();
        return;
      }
      // SogniMusic's --styles: the file's styles as its list ("Funk|Hard Rock|Metal").
      if ("params-field:--styles".equals(field.getTag())) {
        String list = ProgramParams.styleList(text);
        field.setText(list);
        int n = list.isEmpty() ? 0 : list.split("\\|").length;
        android.widget.Toast.makeText(activity, n + (n == 1 ? " style" : " styles") + " from " + file.getName(), android.widget.Toast.LENGTH_SHORT).show();
        return;
      }
      // A definitions file (App:, Prompt:, Loras:, ---- between them): its fields fill their rows.
      if (ParamDefs.looksLike(text) && defsFrom(activity, field, file.getName(), text)) return;
      field.setText(text);
      field.setSelection(0);
    } catch (Exception ex) {
      android.widget.Toast.makeText(activity, "Could not read " + file.getName(), android.widget.Toast.LENGTH_LONG).show();
    }
  }

  /**
   * A definitions file for the Params shown: the one for this program fills its rows (several: a
   * choice first). False when it holds no definitions (the text goes into the prompt as it is).
   */
  static boolean defsFrom(final Activity activity, final EditText prompt, String fileName, String text) {
    final List<ProgramParams.Param> ps = shownParams;
    java.util.Set<String> tokens = new java.util.HashSet<String>();
    if (ps != null) for (ProgramParams.Param p : ps) tokens.add(p.token);
    List<ParamDefs.Def> all = ParamDefs.parse(text, tokens);
    if (all.isEmpty()) return false;
    final List<ParamDefs.Def> mine = ParamDefs.forProgram(all, shownFor);
    if (mine.isEmpty()) {
      android.widget.Toast.makeText(activity, fileName + " is for " + ParamDefs.apps(all) + ", not " + shownFor, android.widget.Toast.LENGTH_LONG).show();
      return true;
    }
    if (mine.size() == 1) {
      fillDef(activity, prompt, mine.get(0));
      return true;
    }
    String[] labels = new String[mine.size()];
    for (int i = 0; i < labels.length; i++) labels[i] = mine.get(i).label();
    lastDefs = new AlertDialog.Builder(activity).setTitle(fileName + ": " + mine.size() + " definitions")
        .setItems(labels, (d, w) -> fillDef(activity, prompt, mine.get(w)))
        .setNegativeButton("Cancel", null).show();
    return true;
  }

  /** A definition's fields into the Params' rows (the prompt into its field); those the program has not are said. */
  static void fillDef(Activity activity, EditText prompt, ParamDefs.Def d) {
    List<String> filled = new java.util.ArrayList<String>(), missing = new java.util.ArrayList<String>();
    for (java.util.Map.Entry<String, String> e : d.fields.entrySet()) {
      EditText target = null;
      if (shownParams != null && shownFields != null) {
        for (int i = 0; i < shownParams.size() && i < shownFields.length; i++) if (shownParams.get(i).token.equals(e.getKey())) target = shownFields[i];
      }
      if (target == null && e.getKey().equals("--prompt")) target = prompt;
      if (target == null) {
        missing.add(e.getKey());
        continue;
      }
      target.setText(e.getValue());
      filled.add(e.getKey());
    }
    android.widget.Toast.makeText(activity, "Filled " + String.join(", ", filled) + (missing.isEmpty() ? "" : "; " + shownFor + " has no " + String.join(", ", missing)),
        android.widget.Toast.LENGTH_LONG).show();
  }

  /** The picker's file, copied into PyJav's input folder, for the waiting file row (or prompt field). */
  public static void filePicked(String path) {
    if (pendingPrompt != null) {
      EditText field = pendingPrompt;
      pendingPrompt = null;
      if (path != null && field.getContext() instanceof Activity) promptFrom((Activity) field.getContext(), field, new java.io.File(path));
      return;
    }
    if (pendingValues == null || pendingIndex < 0 || pendingIndex >= pendingValues.length || path == null) return;
    if (pendingLabel != null && pendingLabel.getContext() instanceof Activity) assign((Activity) pendingLabel.getContext(), pendingValues, pendingIndex, pendingLabel, path, null);
    else pendingValues[pendingIndex] = path;
    pendingValues = null;
    pendingIndex = -1;
    pendingLabel = null;
  }

  /** The Params shown (their rows, values and file rows' labels): a video picked for a picture row fills its frames in. */
  static List<ProgramParams.Param> shownParams;
  /** The text fields of the Params shown last (a definitions file fills several: ParamDefs). */
  static EditText[] shownFields;
  /** The choice of definitions shown last, for the tests. */
  static AlertDialog lastDefs;
  static String[] shownValues;

  /** Whether the Params shown have this row as an Import row. */
  static boolean importRow(String token) {
    if (shownParams == null) return false;
    for (ProgramParams.Param p : shownParams) if (p.token.equals(token) && p.importList) return true;
    return false;
  }

  /** The value in the Params shown of the row with this switch (a drop-down row's too), for the tests; null: no such row. */
  static String fieldsShown(String token) {
    if (shownParams == null || shownFields == null) return null;
    for (int i = 0; i < shownParams.size() && i < shownFields.length; i++) {
      if (shownParams.get(i).token.equals(token) && shownFields[i] != null) return shownFields[i].getText().toString();
    }
    return null;
  }
  /** The tab the Params page showed last (vst_render's), opened again next time. */
  static String paramsTab;

  /** The Params page's rows: each view's tab (vst_render's), whether its showWhen lets it show, and showing them again. */
  static final java.util.Map<android.view.View, String> tabOf = new java.util.HashMap<android.view.View, String>();
  static java.util.function.Predicate<android.view.View> rowShown = v -> true;
  static Runnable refreshRows = () -> {};

  /**
   * A drop-down row (vst_render's Model A, Model B...): its list picks the row's value, kept in a field that
   * is not shown (fields[i], as the typed rows' are); the field set (Defaults, a section) moves the list.
   */
  static android.view.View dropdown(Activity activity, final ProgramParams.Param p, String value, EditText[] fields, int i) {
    final EditText field = new EditText(activity);
    field.setTag("params-field:" + p.token);
    field.setText(value == null ? "" : value.trim());
    fields[i] = field;
    final android.widget.Spinner list = new android.widget.Spinner(activity);
    list.setTag("params-dropdown:" + p.token);
    android.widget.ArrayAdapter<String> items = new android.widget.ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, p.choices);
    items.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    list.setAdapter(items);
    final List<String> vals = java.util.Arrays.asList(p.choiceValues != null ? p.choiceValues : p.choices);
    list.setSelection(Math.max(0, vals.indexOf(field.getText().toString().trim())));
    list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(android.widget.AdapterView<?> parent, View v, int at, long id) {
        String want = p.choiceValue(at);
        if (!want.equals(field.getText().toString().trim())) field.setText(want);
      }

      @Override
      public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    });
    field.addTextChangedListener(new android.text.TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence t, int a, int b, int c) {}

      @Override
      public void onTextChanged(CharSequence t, int a, int b, int c) {}

      @Override
      public void afterTextChanged(android.text.Editable t) {
        int at = Math.max(0, vals.indexOf(t.toString().trim()));
        if (list.getSelectedItemPosition() != at) list.setSelection(at);
      }
    });
    return list;
  }

  /**
   * vst_render's rows on tabs (Input, Output, Plugins, Settings): a strip of tab buttons above the
   * rows, each row shown only on its tab; the Plugins tab ends with the favourite, recently used and
   * folder lists. Nothing changes for a program without tabs.
   */
  static void onTabs(final Activity activity, final LinearLayout box, int[] rowStarts, final List<ProgramParams.Param> ps, final String[] values,
      final android.widget.CheckBox[] uses) {
    final List<String> names = ProgramParams.tabs(ps);
    if (names.isEmpty()) return;
    final int total = box.getChildCount();
    final java.util.Map<android.view.View, String> on = new java.util.LinkedHashMap<android.view.View, String>();
    String last = names.get(names.size() - 1);
    for (int i = 0; i < ps.size(); i++) {
      int end = i + 1 < ps.size() ? rowStarts[i + 1] : total;
      for (int k = rowStarts[i]; k < end; k++) {
        on.put(box.getChildAt(k), ps.get(i).tab != null ? ps.get(i).tab : last);
        tabOf.put(box.getChildAt(k), ps.get(i).tab != null ? ps.get(i).tab : last);
      }
    }
    android.view.View picks = pluginPicks(activity, ps, values, uses);
    box.addView(picks);
    on.put(picks, ProgramParams.PLUGINS);
    if (names.contains(ProgramParams.FILES)) {
      android.view.View files = filesPanel(activity);
      box.addView(files);
      on.put(files, ProgramParams.FILES);
    }
    final LinearLayout strip = new LinearLayout(activity);
    strip.setOrientation(LinearLayout.HORIZONTAL);
    strip.setTag("params-tabs");
    final List<android.widget.Button> buttons = new ArrayList<android.widget.Button>();
    // The tab open last, also after Pulsekit is started again (params-tab.txt).
    final File tabKept = PluginFiles.paramsTabFile(activity.getFilesDir());
    if (paramsTab == null && PluginFiles.kept(tabKept).length() > 0) paramsTab = PluginFiles.kept(tabKept);
    final java.util.function.Consumer<String> show = t -> {
      paramsTab = t;
      PluginFiles.keep(tabKept, t);
      for (java.util.Map.Entry<android.view.View, String> e : on.entrySet()) e.getKey().setVisibility(t.equals(e.getValue()) && rowShown.test(e.getKey()) ? android.view.View.VISIBLE : android.view.View.GONE);
      for (android.widget.Button b : buttons) b.setAlpha(("params-tab:" + t).equals(b.getTag()) ? 1f : 0.55f);
    };
    for (final String t : names) {
      android.widget.Button b = new android.widget.Button(activity);
      b.setText(t);
      b.setTag("params-tab:" + t);
      b.setOnClickListener(v -> show.accept(t));
      buttons.add(b);
      strip.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }
    box.addView(strip, rowStarts.length > 0 ? rowStarts[0] : box.getChildCount());
    show.accept(paramsTab != null && names.contains(paramsTab) ? paramsTab : names.get(0));
  }

  /** The Plugins tab's lists: Favourites, Recently used (vst_render's runs) and Plugin folders; a plugin tapped: Use as plugin 1 to 4. */
  static android.view.View pluginPicks(final Activity activity, final List<ProgramParams.Param> ps, final String[] values, final android.widget.CheckBox[] uses) {
    PluginLibrary.load(activity.getFilesDir());
    LinearLayout col = new LinearLayout(activity);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setTag("params-plugin-picks");
    col.setPadding(0, dp(activity, 10), 0, 0);
    List<String> fav = new ArrayList<String>();
    for (String f : PluginLibrary.favouritePaths()) {
      PluginLibrary.Type t = PluginLibrary.typeOf(new File(f));
      if (t != null && (PluginLibrary.VST3.equals(t.name) || PluginLibrary.VST2.equals(t.name))) fav.add(f);
    }
    pickList(activity, col, "Favourites", "params-favourites", fav, "No favourite plugins yet", ps, values, uses);
    pickList(activity, col, "Recently used", "params-recent", PluginLibrary.recent(), "None yet: the plugins of vst_render's runs", ps, values, uses);
    TextView head = new TextView(activity);
    head.setText("Plugin folders");
    head.setTextSize(14);
    head.setPadding(0, dp(activity, 8), 0, dp(activity, 2));
    col.addView(head);
    List<String> folders = new ArrayList<String>();
    String home = System.getProperty("user.home", "");
    for (String type : new String[] {PluginLibrary.VST3, PluginLibrary.VST2})
      for (String f : PluginLibrary.folders(type, true, home)) if (!folders.contains(f)) folders.add(f);
    TextView list = new TextView(activity);
    list.setTag("params-plugin-folders");
    list.setTextSize(12);
    list.setText(folders.isEmpty() ? "None on the phone: VST plugins play on the desktop (its Params' Folders\u2026 sets them)" : String.join("\n", folders));
    col.addView(list);
    return col;
  }

  static AlertDialog useMenu;

  /**
   * The Files &amp; locations tab: the note map, map.txt (Edit: its text, Pads &amp; notes beside it); Carla's
   * places are the desktop's (vst_render runs there).
   */
  static android.view.View filesPanel(final Activity activity) {
    LinearLayout col = new LinearLayout(activity);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setTag("params-files");
    col.setPadding(0, dp(activity, 10), 0, 0);
    TextView carla = new TextView(activity);
    carla.setTag("params-files-carla");
    carla.setTextSize(12);
    carla.setText("Carla's program and rack plugin: set on the desktop (its Files & locations tab), where vst_render plays the plugins.");
    col.addView(carla);
    // Note names: as the plugin names them (DigiDrum's C-4 is note 48); the pads, Test and map.txt follow it.
    TextView namesHead = new TextView(activity);
    namesHead.setText(PluginFiles.NOTE_NAMES);
    namesHead.setTextSize(14);
    namesHead.setPadding(0, dp(activity, 10), 0, dp(activity, 2));
    col.addView(namesHead);
    PluginFiles.loadNoteNames(activity.getFilesDir());
    final android.widget.Button naming = new android.widget.Button(activity);
    naming.setTag("params-files-note-names");
    naming.setAllCaps(false);
    naming.setText(PluginFiles.label());
    naming.setOnClickListener(v -> namesMenu = new AlertDialog.Builder(activity).setTitle("Note names").setItems(Engine.NOTE_NAMING_LABELS, (dd, which) -> {
      PluginFiles.saveNoteNames(activity.getFilesDir(), Engine.NOTE_NAMINGS[which]);
      naming.setText(PluginFiles.label());
      if (activity instanceof MainActivity) ((MainActivity) activity).playback.refreshPadLabels();
    }).setNegativeButton("Cancel", null).show());
    col.addView(naming);
    TextView head = new TextView(activity);
    head.setText(PluginFiles.NOTE_MAP);
    head.setTextSize(14);
    head.setPadding(0, dp(activity, 10), 0, dp(activity, 2));
    col.addView(head);
    final File map = PluginFiles.noteMap(activity.getFilesDir());
    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    android.widget.Button edit = new android.widget.Button(activity);
    edit.setText("Edit");
    edit.setTag("params-files-map-edit");
    edit.setOnClickListener(v -> editNoteMap(activity, map));
    android.widget.Button notes = new android.widget.Button(activity);
    notes.setText(PluginFiles.PAD_NOTES);
    notes.setTag("params-files-pad-notes");
    notes.setOnClickListener(v -> padNotes(activity));
    row.addView(edit);
    row.addView(notes);
    col.addView(row);
    return col;
  }

  static AlertDialog mapEditor, padNotesShown, namesMenu;
  static EditText mapText;

  /** map.txt's editor: its text (the template when new); Save, Pads & notes, Cancel. */
  static void editNoteMap(final Activity activity, final File map) {
    final EditText text = new EditText(activity);
    text.setTag("params-files-map-text");
    text.setText(PluginFiles.mapText(map));
    text.setTypeface(android.graphics.Typeface.MONOSPACE);
    text.setTextSize(13);
    text.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
    text.setMinLines(12);
    mapText = text;
    mapEditor = new AlertDialog.Builder(activity).setTitle("Note map \u00b7 map.txt").setView(text)
        .setPositiveButton("Save", (d, w) -> android.widget.Toast.makeText(activity, PluginFiles.write(map, text.getText().toString()) ? "Saved map.txt" : "Could not save map.txt",
            android.widget.Toast.LENGTH_SHORT).show())
        .setNeutralButton(PluginFiles.PAD_NOTES, null).setNegativeButton("Cancel", null).show();
    // Pads & notes keeps the editor open.
    mapEditor.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> padNotes(activity));
  }

  /** Pulsekit's pads with their MIDI notes. */
  static void padNotes(Activity activity) {
    TextView t = new TextView(activity);
    t.setTag("params-pad-notes-text");
    t.setText(PluginFiles.padNotes());
    t.setTypeface(android.graphics.Typeface.MONOSPACE);
    t.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), 0);
    android.widget.ScrollView sv = new android.widget.ScrollView(activity);
    sv.addView(t);
    padNotesShown = new AlertDialog.Builder(activity).setTitle(PluginFiles.PAD_NOTES).setView(sv).setPositiveButton("OK", null).show();
  }

  private static void pickList(final Activity activity, LinearLayout col, String title, String tag, List<String> paths, String empty,
      final List<ProgramParams.Param> ps, final String[] values, final android.widget.CheckBox[] uses) {
    TextView head = new TextView(activity);
    head.setText(title);
    head.setTextSize(14);
    head.setPadding(0, dp(activity, 8), 0, dp(activity, 2));
    col.addView(head);
    if (paths.isEmpty()) {
      TextView none = new TextView(activity);
      none.setTag(tag + "-empty");
      none.setTextSize(12);
      none.setText(empty);
      col.addView(none);
      return;
    }
    for (final String path : paths) {
      TextView line = new TextView(activity);
      line.setTag(tag + ":" + new File(path).getName());
      line.setText(new File(path).getName());
      line.setPadding(0, dp(activity, 6), 0, dp(activity, 6));
      line.setOnClickListener(v -> {
        final List<String> rows = ProgramParams.pluginRows(ps);
        String[] items = new String[rows.size()];
        for (int i = 0; i < items.length; i++) items[i] = ProgramParams.useAs(rows.get(i));
        useMenu = new AlertDialog.Builder(activity).setTitle(new File(path).getName()).setItems(items, (d, which) -> usePlugin(ps, values, uses, rows.get(which), path))
            .setNegativeButton("Cancel", null).show();
      });
      // A long press: the same Use as plugin 1 to 4.
      line.setOnLongClickListener(v -> {
        v.performClick();
        return true;
      });
      col.addView(line);
    }
  }

  /** A plugin put on a row (and its Use ticked). */
  static void usePlugin(List<ProgramParams.Param> ps, String[] values, android.widget.CheckBox[] uses, String token, String path) {
    for (int i = 0; i < ps.size(); i++) {
      if (!ps.get(i).token.equals(token)) continue;
      values[i] = path;
      if (shownLabels != null && shownLabels[i] != null) shownLabels[i].setText(fileLabel(path));
      if (uses[i] != null) uses[i].setChecked(true);
    }
  }

  static TextView[] shownLabels;

  /**
   * A file picked for row `index`: its path, shown (`from`: " · from DB"); a video for a picture
   * row gives its first frame instead, and its last frame to the next picture row still empty
   * (read off the main thread; a video it cannot read stays as it is).
   */
  static void assign(final Activity activity, final String[] values, final int index, final TextView label, final String path, final String from) {
    final int[][] frames = values == shownValues ? ProgramParams.framesFor(shownParams, values, index, path) : new int[0][];
    if (frames.length == 0) {
      values[index] = path;
      label.setText(from == null ? fileLabel(path) : new java.io.File(path).getName() + from);
      // A picture: among the recent references (Recent beside a picture row).
      if (path != null && MediaDir.kind(path) == MediaDir.PICTURE) {
        final java.io.File base = activity.getFilesDir();
        new Thread(() -> RecentRefs.add(base, new java.io.File(path)), "pulsekit-recent-ref").start();
      }
      return;
    }
    final String video = new java.io.File(path).getName();
    label.setText("Reading the frames of " + video + "\u2026");
    final TextView[] labels = shownLabels;
    new Thread(() -> {
      final String[] made = new String[frames.length];
      for (int k = 0; k < frames.length; k++) {
        byte[] png = VideoFrames.frame(path, frames[k][1], 0);
        if (png == null) continue;
        try {
          java.io.File dir = new java.io.File(activity.getCacheDir(), "pyjav-in");
          if (!dir.isDirectory()) dir.mkdirs();
          java.io.File out = new java.io.File(dir, video.replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + (frames[k][1] < 0 ? "-last.png" : "-first.png"));
          java.nio.file.Files.write(out.toPath(), png);
          made[k] = out.getAbsolutePath();
        } catch (Exception ex) {
          made[k] = null;
        }
      }
      activity.runOnUiThread(() -> {
        if (made[0] == null) {
          // No frame from it: the video itself, as picked.
          values[index] = path;
          label.setText(fileLabel(path));
          lastFramesNote = "none";
          android.widget.Toast.makeText(activity, "Could not read a frame of " + video, android.widget.Toast.LENGTH_SHORT).show();
          return;
        }
        for (int k = 0; k < frames.length; k++) {
          if (made[k] == null) continue;
          int row = frames[k][0];
          values[row] = made[k];
          TextView shown = row == index ? label : labels != null && row < labels.length ? labels[row] : null;
          if (shown != null) shown.setText(fileLabel(made[k]) + " \u00b7 " + (frames[k][1] < 0 ? "last" : "first") + " frame of " + video);
        }
        lastFramesNote = frames.length > 1 && made[1] != null ? "first and last frame" : "first frame";
      });
    }, "pulsekit-params-frames").start();
  }

  /** What the last video picked for a picture row gave (for the tests). */
  static String lastFramesNote;

  /** The folder picked for the waiting Directory row: kept readable, shown, and opened in the Media browser. */
  public static void dirPicked(MainActivity app, android.net.Uri tree) {
    if (tree == null) return;
    MediaBrowser.keep(app, tree);
    String dir = tree.toString();
    if (pendingValues != null && pendingIndex >= 0 && pendingIndex < pendingValues.length) {
      pendingValues[pendingIndex] = dir;
      // A path field (vst_render's --zip_outdir) shows the folder as it is, to write on.
      if (pendingLabel instanceof android.widget.EditText) pendingLabel.setText(dir);
      else if (pendingLabel != null) pendingLabel.setText(MediaDir.label(dir));
    }
    pendingValues = null;
    pendingIndex = -1;
    pendingLabel = null;
    boolean open = pendingOpensMedia;
    pendingOpensMedia = true;
    if (open) MediaBrowser.open(app, dir);
  }

  /** After a run: --join (SogniVideo's Join with this video) out of the program's saved Params, so it is unticked next time. */
  public static void dropJoin(Activity activity, String program) {
    String saved = load(activity, program);
    if (saved == null || saved.indexOf("--join") < 0) return;
    activity.getSharedPreferences(PREFS, 0).edit().putString(program, ProgramParams.drop(saved, "--join")).apply();
  }

  /** The program's saved Params replaced by `args`. */
  static void store(Activity activity, String program, String args) {
    activity.getSharedPreferences(PREFS, 0).edit().putString(program, args).apply();
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

  /**
   * A row of buttons with a gap between them (they sat edge to edge: Select file, Browse DB...; a
   * folder's Use, Browse and Clear read as one word, "UseBrowseClear").
   */
  static LinearLayout spaced(Activity activity, LinearLayout row) {
    row.setPadding(row.getPaddingLeft(), Math.max(row.getPaddingTop(), dp(activity, 4)), row.getPaddingRight(), row.getPaddingBottom());
    for (int i = 1; i < row.getChildCount(); i++) {
      android.view.View c = row.getChildAt(i);
      if (!(c instanceof android.widget.Button)) continue;
      android.view.ViewGroup.LayoutParams lp = c.getLayoutParams();
      LinearLayout.LayoutParams m = lp instanceof LinearLayout.LayoutParams ? (LinearLayout.LayoutParams) lp
          : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      m.leftMargin = dp(activity, row.getChildAt(i - 1) instanceof android.widget.CheckBox ? 16 : 8);
      c.setLayoutParams(m);
    }
    return row;
  }

  private static int dp(Activity activity, int n) {
    return Math.round(n * activity.getResources().getDisplayMetrics().density);
  }
}
