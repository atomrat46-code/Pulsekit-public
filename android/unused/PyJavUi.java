package pulsekit;

import android.view.View;

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

  public static View.OnClickListener browse(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pkBrowsePyJav();
      }
    };
  }

  public static android.widget.SpinnerAdapter labels(final MainActivity host, final java.util.List labels) {
    return new android.widget.ArrayAdapter<String>(host, 17367048, labels) {
      @Override
      public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
        android.view.View view = super.getView(position, convertView, parent);
        if (view instanceof android.widget.TextView) {
          ((android.widget.TextView) view).setTextColor(0xFFF4F1EA);
        }
        return view;
      }

      @Override
      public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
        android.view.View view = super.getDropDownView(position, convertView, parent);
        if (view instanceof android.widget.TextView) {
          android.widget.TextView text = (android.widget.TextView) view;
          text.setTextColor(0xFFF4F1EA);
          text.setBackgroundColor(0xFF1C1B19);
        }
        return view;
      }
    };
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
