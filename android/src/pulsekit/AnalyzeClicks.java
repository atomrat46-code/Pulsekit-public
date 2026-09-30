package pulsekit;

import android.content.DialogInterface;
import android.view.View;
import android.widget.PopupWindow;
import java.util.ArrayList;

/** Click adapters so Analyze / file-set Info can be wired without javassist anonymous classes. */
public final class AnalyzeClicks {
  private AnalyzeClicks() {}

  public static View.OnClickListener pick(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pickAnalyzeFile();
      }
    };
  }

  public static View.OnClickListener process(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.runAnalyze();
      }
    };
  }

  public static View.OnClickListener prompts(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.openPrompts();
      }
    };
  }

  public static View.OnClickListener pickCompose(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pickComposeFile();
      }
    };
  }

  public static View.OnClickListener processCompose(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.runCompose();
      }
    };
  }

  public static View.OnClickListener fileItem(final MainActivity host, final PopupWindow pop, final String view) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pop.dismiss();
        host.openKitView(view);
      }
    };
  }

  public static View.OnLongClickListener packMenu(
      final MainActivity host,
      final String key,
      final String label,
      final Runnable onDelete,
      final Runnable onExport) {
    return new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        ArrayList<String> items = new ArrayList<String>();
        ArrayList<Runnable> acts = new ArrayList<Runnable>();
        items.add("Info");
        acts.add(new Runnable() {
          @Override
          public void run() {
            host.openFileSetInfo(key, label);
          }
        });
        items.add("Make song");
        acts.add(new Runnable() {
          @Override
          public void run() {
            host.makeFileSetSong(key, label);
          }
        });
        if (host.fileSetStyleOn(key)) {
          items.add("Change style");
          acts.add(new Runnable() {
            @Override
            public void run() {
              AnalyzeClicks.promptChangeStyle(host, key, label);
            }
          });
        }
        if (host.fileSetIsCompose(key)) {
          items.add("Combine tracks");
          acts.add(new Runnable() {
            @Override
            public void run() {
              host.combineFileSetTracks(key, label);
            }
          });
        }
        if (onExport != null) {
          items.add("Export .fset");
          acts.add(onExport);
        }
        items.add("Delete file set");
        if (onDelete != null) acts.add(onDelete);
        new android.app.AlertDialog.Builder(host)
            .setTitle((CharSequence) label)
            .setItems(items.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                if (which >= 0 && which < acts.size()) {
                  Runnable act = acts.get(which);
                  if (act != null) act.run();
                }
              }
            })
            .show();
        return true;
      }
    };
  }

  public static View.OnClickListener closeInfo(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.closeFileSetInfo();
      }
    };
  }

  public static View.OnClickListener fileMakeSong(
      final MainActivity host, final String key, final String label) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.makeFileSetSong(key, label);
      }
    };
  }

  public static void promptChangeStyle(final MainActivity host, final String key, final String label) {
    final String[] names = host.styleDbNames();
    if (names == null || names.length == 0) return;
    final int current = host.currentStyleDbIndex(key);
    final int initial = current >= 0 ? current : 0;
    final int[] pick = new int[] { initial };
    final android.widget.ArrayAdapter<String> rows = new android.widget.ArrayAdapter<String>(
        host, android.R.layout.select_dialog_singlechoice, android.R.id.text1, names) {
      @Override
      public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
        android.view.View view = super.getView(position, convertView, parent);
        android.widget.TextView tv = (android.widget.TextView) view.findViewById(android.R.id.text1);
        if (tv != null) {
          int flags = tv.getPaintFlags();
          if (position == current) flags |= android.graphics.Paint.UNDERLINE_TEXT_FLAG;
          else flags &= ~android.graphics.Paint.UNDERLINE_TEXT_FLAG;
          tv.setPaintFlags(flags);
        }
        return view;
      }
    };
    final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(host)
        .setTitle("Style database")
        .setSingleChoiceItems(rows, initial, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface d, int which) {
            pick[0] = which;
          }
        })
        .setPositiveButton("Change style", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface d, int w) {
            host.applyStylePick(key, label, pick[0]);
          }
        })
        .setNegativeButton("Cancel", null)
        .create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
      @Override
      public void onShow(DialogInterface d) {
        android.widget.ListView list = dialog.getListView();
        if (list != null) list.setSelection(initial);
      }
    });
    dialog.show();
  }

  public static View.OnClickListener fileChangeStyle(
      final MainActivity host, final String key, final String label) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AnalyzeClicks.promptChangeStyle(host, key, label);
      }
    };
  }

  public static View.OnClickListener fileCombine(
      final MainActivity host, final String key, final String label) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.combineFileSetTracks(key, label);
      }
    };
  }

  /** Tag is fp: pattern id, ff: fill id, or fr: Fillern pattern id. */
  public static View.OnClickListener fileEntry(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Object tag = v.getTag();
        if (tag == null) return;
        String s = tag.toString();
        if (s.startsWith("fp:")) host.pkLoadFilePattern(s.substring(3));
        else if (s.startsWith("ff:")) host.pkLoadFileFill(s.substring(3));
        else if (s.startsWith("fr:")) host.pkLoadFileFillern(s.substring(3));
      }
    };
  }

  public static View.OnClickListener filePlayMidi(final MainActivity host, final String src) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.playSourceMidi(src);
      }
    };
  }

  public static View.OnClickListener filePauseMidi(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.pauseSourceMidi();
      }
    };
  }

  public static View.OnClickListener fileStopMidi(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.stopSourceMidi();
      }
    };
  }

  public static View.OnClickListener filePlayCombined(final MainActivity host, final String src) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.playCombinedFile(src);
      }
    };
  }

  public static View.OnClickListener fileStopCombined(final MainActivity host) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.stopCombinedFile();
      }
    };
  }

  public static View.OnClickListener fileSaveCombined(final MainActivity host, final String src) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        host.saveCombinedFile(src);
      }
    };
  }

  public static DialogInterface.OnClickListener makeSong(
      final MainActivity host, final String name, final java.util.List parts) {
    return new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        host.addImportedMidiSong(name, parts);
      }
    };
  }
}
