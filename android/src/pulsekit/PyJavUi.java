package pulsekit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Click and result adapters for the PyJav tab. Compiled into the APK. */
public final class PyJavUi implements JavaRun.Listener {
  private final MainActivity host;
  /** The run this result is for: its pending entry, arguments, Save this run into DB and source (null: the tests' plain listener). */
  private final String pending;
  private final java.util.List<String> argv;
  private final boolean saveRun;
  private final String src;
  private final boolean ownRun;

  private PyJavUi(MainActivity host) {
    this.host = host;
    this.pending = null;
    this.argv = null;
    this.saveRun = false;
    this.src = null;
    this.ownRun = false;
  }

  private PyJavUi(MainActivity host, String pending, java.util.List<String> argv, boolean saveRun, String src) {
    this.host = host;
    this.pending = pending;
    this.argv = argv;
    this.saveRun = saveRun;
    this.src = src;
    this.ownRun = true;
  }

  public static View.OnClickListener mode(final MainActivity host, final String mode) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pyJav.pkSetPromptRun(mode);
      }
    };
  }

  public static View.OnClickListener click(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pyJav.pkRunPyJav();
      }
    };
  }

  /** Ask which installed subsystem should run the prompt when more than one is present. */
  public static void chooseAi(final MainActivity host, final String prompt, final String ref1, final String ref2) {
    final String[][] found = Subsystem.available(host);
    if (found.length == 0) {
      host.pyJav.pkShowAiResult("AI prompt. PyJav cannot run this on Android. Grok, Sogni, and Claude are not present.");
      return;
    }
    if (found.length == 1) {
      host.pyJav.pkShowAiResult(Subsystem.openPackage(host, found[0][0], found[0][1], prompt, ref1, ref2));
      return;
    }
    final String[] labels = new String[found.length];
    for (int i = 0; i < found.length; i++) labels[i] = found[i][0];
    new AlertDialog.Builder(host)
        .setTitle("Which system should run this prompt?")
        .setItems(labels, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            host.pyJav.pkShowAiResult(Subsystem.openPackage(host, found[which][0], found[which][1], prompt, ref1, ref2));
          }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            host.pyJav.pkShowAiResult("Cancelled.");
          }
        })
        .show();
  }

  /** Asks for a file name when the output format is not known. Blank keeps the log. */
  public static void askOutput(final MainActivity host) {
    final EditText field = new EditText(host);
    field.setSingleLine(true);
    field.setHint("name.png");
    new AlertDialog.Builder(host)
        .setTitle("Output file name")
        .setMessage("The output format is unclear. Enter a name and extension, or leave blank and PyJav will write a log.")
        .setView(field)
        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            host.pyJav.pkAcceptOutput(field.getText().toString());
          }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            host.pyJav.pkCancelOutput();
          }
        })
        .show();
  }

  public static View.OnClickListener browse(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pyJav.pkBrowsePyJav();
      }
    };
  }

  public static android.widget.SpinnerAdapter labels(final MainActivity host, final java.util.List labels) {
    android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(host, 17367043, labels) {
      @Override
      public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
        return row(position, convertView);
      }

      @Override
      public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
        return row(position, convertView);
      }

      private android.widget.TextView row(int position, android.view.View convertView) {
        android.widget.TextView text = convertView instanceof android.widget.TextView ? (android.widget.TextView) convertView : new android.widget.TextView(host);
        text.setText(getItem(position));
        text.setTextColor(0xFFECEBE6);
        text.setTextSize(15);
        text.setBackgroundColor(0xFF1B1D1F);
        int pad = (int) (host.getResources().getDisplayMetrics().density * 12);
        text.setPadding(pad, pad, pad, pad);
        text.setMinHeight((int) (host.getResources().getDisplayMetrics().density * 44));
        return text;
      }
    };
    return adapter;
  }

  /** Recent programs under a collapsed "Select program" row. The spinner popup draws no rows on this screen. */
  /** A program's runs, as a long press on it in Recent showed them last (for the tests). */
  static android.app.AlertDialog lastRuns;

  public static void fillRecent(final MainActivity host, android.view.View anchor, java.util.List items) {
    if (host == null || anchor == null) return;
    android.view.ViewParent parent = anchor.getParent();
    if (!(parent instanceof LinearLayout)) return;
    LinearLayout pane = (LinearLayout) parent;
    android.view.View old = pane.findViewWithTag("pk-recent");
    if (old != null) pane.removeView(old);
    anchor.setVisibility(View.GONE);
    LinearLayout box = new LinearLayout(host);
    box.setTag("pk-recent");
    box.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (host.getResources().getDisplayMetrics().density * 12);
    // Collapsed like an HTML <select>: the list opens from "Select program", so a stray tap
    // while scrolling does not change the program.
    final LinearLayout list = new LinearLayout(host);
    list.setTag("pk-recent-list");
    list.setOrientation(LinearLayout.VERTICAL);
    list.setVisibility(View.GONE);
    final TextView toggle = new TextView(host);
    toggle.setTag("pk-recent-toggle");
    toggle.setText("Select program \u25be");
    toggle.setTextColor(0xFFECEBE6);
    toggle.setTextSize(15);
    toggle.setTypeface(Typeface.DEFAULT_BOLD);
    toggle.setBackgroundColor(0xFF1B1D1F);
    toggle.setPadding(pad, pad, pad, pad);
    toggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        boolean open = list.getVisibility() != View.VISIBLE;
        list.setVisibility(open ? View.VISIBLE : View.GONE);
        toggle.setText(open ? "Select program \u25b4" : "Select program \u25be");
      }
    });
    LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
    tlp.topMargin = pad / 2;
    tlp.bottomMargin = pad / 2;
    box.addView(toggle, tlp);
    box.addView(list);
    // Each program once: a tap runs it (without parameters when such a run is remembered, else
    // with its last ones); a long press lists its runs, a tap on one runs it with those.
    final java.util.List<PyJavRecent.Item> runs = new java.util.ArrayList<PyJavRecent.Item>();
    if (items != null) for (Object row : items) if (row instanceof PyJavRecent.Item) runs.add((PyJavRecent.Item) row);
    java.util.List<String> programs = PyJavRecent.programs(runs);
    if (programs.isEmpty()) {
      TextView empty = new TextView(host);
      empty.setText("No recent programs");
      empty.setTextColor(0xFF8A8B86);
      empty.setTextSize(15);
      empty.setPadding(pad, pad, pad, pad);
      list.addView(empty);
    } else {
      for (final String name : programs) {
        TextView button = new TextView(host);
        button.setText(name);
        button.setTag("pk-recent-item:" + name);
        button.setTextColor(0xFFECEBE6);
        button.setTextSize(15);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setBackgroundColor(0xFF15171A);
        button.setPadding(pad * 2, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = pad / 2;
        button.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            list.setVisibility(View.GONE);
            toggle.setText("Select program \u25be");
            host.pyJav.pkRunRecent(runs.indexOf(PyJavRecent.tapped(runs, name)) + 1);
          }
        });
        button.setOnLongClickListener(new View.OnLongClickListener() {
          @Override
          public boolean onLongClick(View v) {
            final java.util.List<PyJavRecent.Item> mine = PyJavRecent.runsOf(runs, name);
            CharSequence[] labels = new CharSequence[mine.size()];
            for (int i = 0; i < mine.size(); i++) labels[i] = PyJavRecent.argsLabel(mine.get(i));
            lastRuns = new android.app.AlertDialog.Builder(host)
                .setTitle(name)
                .setItems(labels, (d, which) -> {
                  list.setVisibility(View.GONE);
                  toggle.setText("Select program \u25be");
                  host.pyJav.pkRunRecent(runs.indexOf(mine.get(which)) + 1);
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
          }
        });
        list.addView(button, lp);
      }
    }
    int at = 1;
    if (at > pane.getChildCount()) at = pane.getChildCount();
    pane.addView(box, at);
  }

  public static View.OnClickListener params(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pyJav.pkOpenParams();
      }
    };
  }

  public static View.OnClickListener browseInput(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pyJav.pkBrowseInput();
      }
    };
  }

  public static android.widget.AdapterView.OnItemSelectedListener recent(final MainActivity host) {
    return new android.widget.AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
        host.pyJav.pkApplyRecent(position);
      }

      @Override
      public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    };
  }

  public static JavaRun.Listener listener(MainActivity host) {
    return new PyJavUi(host);
  }

  /** The listener of one run: its result is shown with that run's own pending entry and arguments. */
  public static JavaRun.Listener listener(MainActivity host, String pending, java.util.List<String> argv, boolean saveRun, String src) {
    return new PyJavUi(host, pending, argv, saveRun, src);
  }

  @Override
  public void onDone(final JavaRun.Result result) {
    host.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (ownRun) host.pyJav.pkShowPyResult(result, pending, argv, saveRun, src);
        else host.pyJav.pkShowPyResult(result);
      }
    });
  }
}
