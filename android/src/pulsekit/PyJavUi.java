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

  private PyJavUi(MainActivity host) {
    this.host = host;
  }

  public static View.OnClickListener mode(final MainActivity host, final String mode) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pkSetPromptRun(mode);
      }
    };
  }

  public static View.OnClickListener click(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pkRunPyJav();
      }
    };
  }

  /** Ask which installed subsystem should run the prompt when more than one is present. */
  public static void chooseAi(final MainActivity host, final String prompt, final String ref1, final String ref2) {
    final String[][] found = Subsystem.available(host);
    if (found.length == 0) {
      host.pkShowAiResult("AI prompt. PyJav cannot run this on Android. Grok, Sogni, and Claude are not present.");
      return;
    }
    if (found.length == 1) {
      host.pkShowAiResult(Subsystem.openPackage(host, found[0][0], found[0][1], prompt, ref1, ref2));
      return;
    }
    final String[] labels = new String[found.length];
    for (int i = 0; i < found.length; i++) labels[i] = found[i][0];
    new AlertDialog.Builder(host)
        .setTitle("Which system should run this prompt?")
        .setItems(labels, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            host.pkShowAiResult(Subsystem.openPackage(host, found[which][0], found[which][1], prompt, ref1, ref2));
          }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            host.pkShowAiResult("Cancelled.");
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
            host.pkAcceptOutput(field.getText().toString());
          }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            host.pkCancelOutput();
          }
        })
        .show();
  }

  public static View.OnClickListener browse(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pkBrowsePyJav();
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

  /** Visible recent programs. The spinner popup draws no rows on this screen. */
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
    TextView caption = new TextView(host);
    caption.setText("Recent");
    caption.setTextColor(0xFF8A8B86);
    caption.setTextSize(12);
    caption.setTypeface(Typeface.DEFAULT_BOLD);
    caption.setPadding(0, pad, 0, pad / 2);
    box.addView(caption);
    int count = items == null ? 0 : items.size();
    if (count == 0) {
      TextView empty = new TextView(host);
      empty.setText("No recent programs");
      empty.setTextColor(0xFF8A8B86);
      empty.setTextSize(15);
      empty.setPadding(pad, pad, pad, pad);
      box.addView(empty);
    } else {
      for (int i = 0; i < count; i++) {
        Object row = items.get(i);
        String name = "program";
        if (row instanceof PyJavRecent.Item) {
          PyJavRecent.Item item = (PyJavRecent.Item) row;
          name = item.label();
        } else if (row != null) {
          name = row.toString();
        }
        final int pick = i + 1;
        TextView button = new TextView(host);
        button.setText(name);
        button.setTextColor(0xFFECEBE6);
        button.setTextSize(15);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setBackgroundColor(0xFF1B1D1F);
        button.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = pad / 2;
        button.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            host.pkApplyRecent(pick);
          }
        });
        box.addView(button, lp);
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
        host.pkOpenParams();
      }
    };
  }

  public static View.OnClickListener browseInput(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pkBrowseInput();
      }
    };
  }

  public static android.widget.AdapterView.OnItemSelectedListener recent(final MainActivity host) {
    return new android.widget.AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
        host.pkApplyRecent(position);
      }

      @Override
      public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    };
  }

  public static JavaRun.Listener listener(MainActivity host) {
    return new PyJavUi(host);
  }

  @Override
  public void onDone(final JavaRun.Result result) {
    host.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        host.pkShowPyResult(result);
      }
    });
  }
}
