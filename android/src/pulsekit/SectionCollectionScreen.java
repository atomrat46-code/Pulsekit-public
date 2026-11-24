package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Section Collection (Browse sections' SC) on the phone: the program's groups on the left (Add new at
 * the top; each with (n) and + to open its sections; held: Delete, Rename), a line, then the sections
 * in no group on the right (Add new section at the top right; each its text in a box, its name above;
 * held: Save, Delete, Move into group, Edit name). A section's ☰ dragged onto a group puts it there; on
 * the left, a section's ☰ is dragged onto another group. Close at the bottom right.
 */
final class SectionCollectionScreen {
  private final Activity activity;
  private final String app;
  /** The LoRAs the Params have now (Add new section lists them first), or "". */
  private final String lorasNow;
  final LinearLayout left;
  final LinearLayout right;
  final AlertDialog dialog;
  /** The groups open (+ pressed), by name in lower case. */
  final Set<String> open = new HashSet<String>();

  /** The window shown last, its menu and the question asked last (the tests). */
  static SectionCollectionScreen last;
  /** The menu shown last (a list: a section's or a group's, or Move into group's groups), the full view shown last. */
  static AlertDialog lastMenu, lastFull;
  static AlertDialog lastAsk;

  /** The program's other rows a section keeps (Aspect, Width, Height...), from its Params; may be none. */
  private final List<ProgramParams.Param> extras;

  private SectionCollectionScreen(Activity activity, String app, String lorasNow, List<ProgramParams.Param> ps, final Runnable closed) {
    this.activity = activity;
    this.app = app;
    this.extras = SectionCollection.extraRows(ps);
    this.lorasNow = lorasNow == null ? "" : lorasNow;
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.HORIZONTAL);
    // Left: Add new, then the groups (scrolled).
    LinearLayout leftSide = new LinearLayout(activity);
    leftSide.setOrientation(LinearLayout.VERTICAL);
    android.widget.Button addGroup = button(SectionCollection.ADD_GROUP, "sc-add-group");
    addGroup.setOnClickListener(v -> this.addGroup());
    leftSide.addView(addGroup, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    this.left = new LinearLayout(activity);
    this.left.setOrientation(LinearLayout.VERTICAL);
    ScrollView ls = new ScrollView(activity);
    ls.addView(this.left);
    leftSide.addView(ls, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    // The line between them.
    View line = new View(activity);
    line.setBackgroundColor(0xFF888888);
    // Right: Add new section at the top right, then the sections in no group (scrolled).
    LinearLayout rightSide = new LinearLayout(activity);
    rightSide.setOrientation(LinearLayout.VERTICAL);
    android.widget.Button addSection = button(SectionCollection.ADD_SECTION, "sc-add-section");
    addSection.setOnClickListener(v -> this.addSection());
    LinearLayout.LayoutParams top = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    top.gravity = android.view.Gravity.END;
    rightSide.addView(addSection, top);
    this.right = new LinearLayout(activity);
    this.right.setOrientation(LinearLayout.VERTICAL);
    ScrollView rs = new ScrollView(activity);
    rs.addView(this.right);
    rightSide.addView(rs, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    root.addView(leftSide, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT);
    lp.setMargins(dp(4), 0, dp(4), 0);
    root.addView(line, lp);
    root.addView(rightSide, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f));
    this.dialog = new AlertDialog.Builder(activity).setTitle(SectionCollection.TITLE + " · " + app).setView(root)
        .setPositiveButton(SectionCollection.CLOSE, null).create();
    this.dialog.setOnDismissListener(d -> {
      if (closed != null) closed.run();
    });
    this.refresh();
  }

  /** Shows the program's Section Collection; `closed` runs when it closes (Browse sections listed again). */
  static SectionCollectionScreen show(Activity activity, String app, Runnable closed) {
    return show(activity, app, PyJavParams.load(activity, app + ".java") == null ? "" : SectionCollection.lorasIn(PyJavParams.load(activity, app + ".java")), null, closed);
  }

  static SectionCollectionScreen show(Activity activity, String app, String lorasNow, Runnable closed) {
    return show(activity, app, lorasNow, null, closed);
  }

  /** Shows it with the LoRAs the Params have now (Add new section's list has them first). */
  /** Shows it with the Params' LoRAs now and the program's rows (Add new section and Edit data have its other rows). */
  static SectionCollectionScreen show(Activity activity, String app, String lorasNow, List<ProgramParams.Param> ps, Runnable closed) {
    SectionCollectionScreen sc = new SectionCollectionScreen(activity, app, lorasNow, ps, closed);
    last = sc;
    sc.dialog.show();
    // Most of the screen: both sides have room for their lists.
    android.util.DisplayMetrics m = activity.getResources().getDisplayMetrics();
    if (sc.dialog.getWindow() != null) sc.dialog.getWindow().setLayout((int) (m.widthPixels * 0.98), (int) (m.heightPixels * 0.9));
    return sc;
  }

  int dp(int n) {
    return Math.round(n * this.activity.getResources().getDisplayMetrics().density);
  }

  android.widget.Button button(String text, String tag) {
    android.widget.Button b = new android.widget.Button(this.activity);
    b.setText(text);
    b.setTag(tag);
    b.setAllCaps(false);
    return b;
  }

  PromptVault vault() throws Exception {
    return PromptVault.open(this.activity.getFilesDir());
  }

  void say(String text) {
    android.widget.Toast.makeText(this.activity, text, android.widget.Toast.LENGTH_LONG).show();
  }

  /** The two-field dialog (group or section, Edit name); `done` gets the texts. */
  void ask(String title, String first, String firstNow, String second, String secondNow, boolean longSecond, String ok, String cancel,
      final java.util.function.BiConsumer<String, String> done) {
    LinearLayout box = new LinearLayout(this.activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(20), dp(8), dp(20), 0);
    final EditText a = new EditText(this.activity);
    a.setTag("sc-ask-first");
    a.setSingleLine(true);
    a.setHint(first);
    a.setText(firstNow);
    final EditText b = new EditText(this.activity);
    b.setTag("sc-ask-second");
    b.setHint(second);
    b.setText(secondNow);
    b.setMinLines(longSecond ? 4 : 2);
    b.setGravity(android.view.Gravity.TOP);
    TextView fl = new TextView(this.activity);
    fl.setText(first);
    TextView sl = new TextView(this.activity);
    sl.setText(second);
    box.addView(fl);
    box.addView(a);
    box.addView(sl);
    box.addView(b);
    lastAsk = new AlertDialog.Builder(this.activity).setTitle(title).setView(box)
        .setPositiveButton(ok, (d, w) -> done.accept(a.getText().toString(), b.getText().toString()))
        .setNegativeButton(cancel, null).show();
  }

  void confirm(String text, final Runnable yes) {
    lastAsk = new AlertDialog.Builder(this.activity).setTitle(SectionCollection.DELETE).setMessage(text)
        .setPositiveButton(SectionCollection.DELETE, (d, w) -> yes.run()).setNegativeButton("Cancel", null).show();
  }

  void addGroup() {
    this.ask(SectionCollection.ADD_GROUP, SectionCollection.GROUP_NAME, "", SectionCollection.DESCRIPTION, "", false, "OK", "Cancel", (name, note) -> {
      try {
        String bad = SectionCollection.addGroup(this.vault(), this.app, name, note);
        if (bad != null) this.say(bad);
      } catch (Exception ex) {
        this.say("Could not add the group: " + ex.getMessage());
      }
      this.refresh();
    });
  }

  /** The section form shown last (the tests). */
  static AlertDialog lastNew;

  /**
   * The section form (Add new section, Edit data, the Params' Save as new): name, description, prompt,
   * LoRAs (a list), group (a list) and the program's other rows; `done` gets the form as OK left it.
   */
  static void form(final Activity activity, String title, SectionCollection.Form init, final List<String> loras, final List<String> groups,
      List<ProgramParams.Param> extras, final java.util.function.Consumer<SectionCollection.Form> done) {
    final float dpx = activity.getResources().getDisplayMetrics().density;
    LinearLayout box = new LinearLayout(activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(Math.round(20 * dpx), Math.round(8 * dpx), Math.round(20 * dpx), 0);
    final EditText name = field(activity, box, SectionCollection.SECTION_NAME, "sc-new-name", 1, init.name);
    final EditText note = field(activity, box, SectionCollection.DESCRIPTION, "sc-new-note", 2, init.note);
    final EditText prompt = field(activity, box, SectionCollection.PROMPT, "sc-new-prompt", 4, init.prompt);
    final android.widget.Spinner lp = pick(activity, box, SectionCollection.LORAS, "sc-new-loras", loras, init.loras);
    final android.widget.Spinner gp = pick(activity, box, SectionCollection.GROUP, "sc-new-group", groups, init.group);
    // The program's other rows (and any other entry the section has): a field each.
    final java.util.LinkedHashMap<String, EditText> other = new java.util.LinkedHashMap<String, EditText>();
    java.util.LinkedHashMap<String, String> labels = new java.util.LinkedHashMap<String, String>();
    for (ProgramParams.Param x : extras) labels.put(x.token, SectionCollection.rowLabel(x));
    for (String k : init.extra.keySet()) if (!labels.containsKey(k)) labels.put(k, k.substring(2));
    for (java.util.Map.Entry<String, String> e : labels.entrySet())
      other.put(e.getKey(), field(activity, box, e.getValue(), "sc-new-row:" + e.getKey(), 1, init.extra.containsKey(e.getKey()) ? init.extra.get(e.getKey()) : ""));
    ScrollView sv = new ScrollView(activity);
    sv.addView(box);
    boolean edit = SectionCollection.EDIT_NAME.equals(title);
    lastNew = new AlertDialog.Builder(activity).setTitle(title).setView(sv)
        .setPositiveButton(edit ? SectionCollection.SAVE : "OK", (d, w) -> {
          SectionCollection.Form f = new SectionCollection.Form();
          f.name = name.getText().toString();
          f.note = note.getText().toString();
          f.prompt = prompt.getText().toString();
          f.loras = loras.get(Math.max(0, lp.getSelectedItemPosition()));
          f.group = groups.get(Math.max(0, gp.getSelectedItemPosition()));
          for (java.util.Map.Entry<String, EditText> e : other.entrySet()) f.extra.put(e.getKey(), e.getValue().getText().toString());
          done.accept(f);
        })
        .setNegativeButton(edit ? SectionCollection.EXIT : "Cancel", null).show();
  }

  /** Add new section: the form, empty (the Params' LoRAs listed first). */
  void addSection() {
    try {
      form(this.activity, SectionCollection.ADD_SECTION, new SectionCollection.Form(), SectionCollection.recentLoras(this.vault(), this.app, this.lorasNow),
          SectionCollection.groupPicks(this.vault(), this.app), this.extras, f -> {
            try {
              String bad = SectionCollection.add(this.vault(), this.app, f);
              if (bad != null) this.say(bad);
            } catch (Exception ex) {
              this.say("Could not add the section: " + ex.getMessage());
            }
            this.refresh();
          });
    } catch (Exception ex) {
      this.say("Could not read the sections: " + ex.getMessage());
    }
  }

  /** Edit data: the section's form, every field (its rows too). */
  void editData(final PromptVault.SogniSection s) {
    SectionCollection.Form init = SectionCollection.formOf(s);
    try {
      form(this.activity, SectionCollection.EDIT_NAME, init, SectionCollection.lorasFor(this.vault(), this.app, this.lorasNow, init),
          SectionCollection.groupPicks(this.vault(), this.app), this.extras, f -> {
            try {
              String bad = SectionCollection.update(this.vault(), s, f);
              if (bad != null) this.say(bad);
            } catch (Exception ex) {
              this.say("Could not save the section: " + ex.getMessage());
            }
            this.refresh();
          });
    } catch (Exception ex) {
      this.say("Could not read the sections: " + ex.getMessage());
    }
  }

  static EditText field(Activity activity, LinearLayout box, String label, String tag, int lines, String now) {
    TextView l = new TextView(activity);
    l.setText(label);
    box.addView(l);
    EditText e = new EditText(activity);
    e.setTag(tag);
    if (lines == 1) e.setSingleLine(true);
    else {
      e.setMinLines(lines);
      e.setGravity(android.view.Gravity.TOP);
    }
    e.setText(now == null ? "" : now);
    box.addView(e);
    return e;
  }

  static android.widget.Spinner pick(Activity activity, LinearLayout box, String label, String tag, List<String> items, String now) {
    TextView l = new TextView(activity);
    l.setText(label);
    l.setPadding(0, Math.round(6 * activity.getResources().getDisplayMetrics().density), 0, 0);
    box.addView(l);
    android.widget.Spinner sp = new android.widget.Spinner(activity);
    sp.setTag(tag);
    android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, items);
    a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sp.setAdapter(a);
    sp.setSelection(Math.max(0, items.indexOf(now)));
    box.addView(sp);
    return sp;
  }


  /** Both sides drawn again from the library. */
  void refresh() {
    this.left.removeAllViews();
    this.right.removeAllViews();
    List<SectionCollection.Group> groups;
    List<PromptVault.SogniSection> loose;
    try {
      groups = SectionCollection.groups(this.vault(), this.app);
      loose = SectionCollection.loose(this.vault(), this.app);
    } catch (Exception ex) {
      TextView t = new TextView(this.activity);
      t.setText("Could not read the sections: " + ex.getMessage());
      this.left.addView(t);
      return;
    }
    if (groups.isEmpty()) this.left.addView(note(SectionCollection.NO_GROUPS));
    for (SectionCollection.Group g : groups) this.left.addView(this.groupRow(g));
    if (loose.isEmpty()) this.right.addView(note(SectionCollection.NO_SECTIONS));
    for (PromptVault.SogniSection s : loose) this.right.addView(this.sectionBox(s));
  }

  TextView note(String text) {
    TextView t = new TextView(this.activity);
    t.setText(text);
    t.setPadding(dp(4), dp(8), dp(4), dp(8));
    return t;
  }

  View groupRow(final SectionCollection.Group g) {
    LinearLayout row = new LinearLayout(this.activity);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setTag("sc-group:" + g.name);
    row.setPadding(0, dp(2), 0, dp(6));
    LinearLayout head = new LinearLayout(this.activity);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(android.view.Gravity.CENTER_VERTICAL);
    TextView name = new TextView(this.activity);
    name.setTag("sc-group-name:" + g.name);
    name.setText(SectionCollection.groupLabel(g));
    name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
    name.setPadding(dp(4), dp(8), dp(4), dp(8));
    head.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    // ⋮: the group's menu (as a long press on its name).
    android.widget.Button more = this.small("\u22ee", "sc-group-menu:" + g.name);
    more.setOnClickListener(v -> this.groupMenu(v, g));
    head.addView(more);
    final boolean isOpen = this.open.contains(g.name.toLowerCase(java.util.Locale.ROOT));
    if (!g.sections.isEmpty()) {
      android.widget.Button plus = button(isOpen ? SectionCollection.SHUT : SectionCollection.OPEN, "sc-open:" + g.name);
      plus.setOnClickListener(v -> {
        String k = g.name.toLowerCase(java.util.Locale.ROOT);
        if (!this.open.remove(k)) this.open.add(k);
        this.refresh();
      });
      head.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(40)));
    }
    row.addView(head);
    if (!g.note.isEmpty()) {
      TextView n = new TextView(this.activity);
      n.setText(g.note);
      n.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC);
      n.setPadding(dp(8), 0, dp(4), dp(2));
      row.addView(n);
    }
    name.setOnLongClickListener(v -> {
      this.groupMenu(v, g);
      return true;
    });
    // A section dropped on the group (its row, its name or one of its sections) goes into it.
    View.OnDragListener drop = (v, e) -> {
      if (e.getAction() == android.view.DragEvent.ACTION_DRAG_STARTED) return true;
      if (e.getAction() == android.view.DragEvent.ACTION_DRAG_ENTERED) v.setBackgroundColor(0x3300AAFF);
      if (e.getAction() == android.view.DragEvent.ACTION_DRAG_EXITED || e.getAction() == android.view.DragEvent.ACTION_DRAG_ENDED) v.setBackgroundColor(0);
      if (e.getAction() == android.view.DragEvent.ACTION_DROP && e.getClipData() != null && e.getClipData().getItemCount() > 0) {
        v.setBackgroundColor(0);
        this.dropOn(g.name, String.valueOf(e.getClipData().getItemAt(0).getText()));
      }
      return true;
    };
    row.setOnDragListener(drop);
    if (isOpen) {
      for (final PromptVault.SogniSection s : g.sections) {
        // Its ⋮ on a row above it, the text the row's whole width.
        LinearLayout one = new LinearLayout(this.activity);
        one.setOrientation(LinearLayout.VERTICAL);
        one.setPadding(dp(10), 0, 0, dp(4));
        TextView label = new TextView(this.activity);
        label.setTag("sc-in:" + s.id);
        label.setText(SectionCollection.shortLabel(s));
        label.setPadding(dp(4), dp(6), dp(4), dp(6));
        label.setOnLongClickListener(v -> {
          this.sectionMenu(v, s, null);
          return true;
        });
        // A tap: the section shown fully (its row may be cut short).
        label.setOnClickListener(v -> this.showFull(s));
        android.widget.Button more2 = this.small("\u22ee", "sc-section-menu:" + s.id);
        more2.setOnClickListener(v -> this.sectionMenu(v, s, null));
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        up.gravity = android.view.Gravity.END;
        one.addView(more2, up);
        one.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(one);
      }
    }
    return row;
  }

  View sectionBox(final PromptVault.SogniSection s) {
    LinearLayout box = new LinearLayout(this.activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setTag("sc-section:" + s.id);
    box.setPadding(dp(2), dp(4), dp(2), dp(8));
    LinearLayout head = new LinearLayout(this.activity);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(android.view.Gravity.CENTER_VERTICAL);
    TextView name = new TextView(this.activity);
    name.setTag("sc-head:" + s.id);
    name.setText(SectionCollection.heading(s));
    name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    head.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    final EditText text = new EditText(this.activity);
    // ⋮: the section's menu (as a long press on its text).
    android.widget.Button more = this.small("\u22ee", "sc-section-menu:" + s.id);
    more.setOnClickListener(v -> this.sectionMenu(v, s, text));
    head.addView(more);
    text.setTag("sc-text:" + s.id);
    text.setText(s.text == null ? "" : s.text.trim());
    text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
    text.setGravity(android.view.Gravity.TOP);
    text.setMinLines(2);
    // Held: the section's menu (the text box's own copy and paste are in it too: Save keeps changes).
    text.setOnLongClickListener(v -> {
      this.sectionMenu(v, s, text);
      return true;
    });
    name.setOnLongClickListener(v -> {
      this.sectionMenu(v, s, text);
      return true;
    });
    box.addView(head);
    box.addView(text);
    return box;
  }

  /** A section dropped on a group: the drag's text read and the section moved. */
  void dropOn(String group, String dragText) {
    long id = SectionCollection.dragged(dragText);
    if (id < 0) return;
    try {
      SectionCollection.move(this.vault(), id, group);
    } catch (Exception ex) {
      this.say("Could not move the section: " + ex.getMessage());
    }
    this.refresh();
  }

  void moveTo(PromptVault.SogniSection s, String group) {
    this.dropOn(group, SectionCollection.dragText(s));
  }

  /** A small button (⋮) for a row's menu. */
  android.widget.Button small(String text, String tag) {
    android.widget.Button b = new android.widget.Button(this.activity, null, android.R.attr.buttonStyleSmall);
    b.setText(text);
    b.setTag(tag);
    b.setAllCaps(false);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setPadding(dp(8), 0, dp(8), 0);
    return b;
  }

  /** A section shown fully (its name, description, when it was saved, its entries). */
  void showFull(PromptVault.SogniSection s) {
    TextView t = new TextView(this.activity);
    t.setTag("sc-full-text");
    t.setText(SogniSections.full(s));
    t.setTextIsSelectable(true);
    t.setPadding(dp(20), dp(8), dp(20), 0);
    ScrollView sv = new ScrollView(this.activity);
    sv.addView(t);
    lastFull = new AlertDialog.Builder(this.activity).setTitle(SogniSections.label(s)).setView(sv).setPositiveButton(SectionCollection.CLOSE, null).show();
  }

  /** A list of items as a menu, each with what it does. */
  AlertDialog menu(String title, final List<String> items, final List<Runnable> does) {
    lastMenu = new AlertDialog.Builder(this.activity).setTitle(title).setItems(items.toArray(new String[0]), (d, w) -> does.get(w).run())
        .setNegativeButton("Cancel", null).show();
    return lastMenu;
  }

  void sectionMenu(View at, final PromptVault.SogniSection s, final EditText text) {
    List<String> items = new java.util.ArrayList<String>();
    List<Runnable> does = new java.util.ArrayList<Runnable>();
    if (text != null) {
      items.add(SectionCollection.SAVE);
      does.add(() -> {
        try {
          String bad = SectionCollection.save(this.vault(), s, text.getText().toString());
          if (bad != null) this.say(bad);
        } catch (Exception ex) {
          this.say("Could not save the section: " + ex.getMessage());
        }
        this.refresh();
      });
    }
    items.add(SectionCollection.CUT);
    does.add(() -> this.hold(s, true));
    items.add(SectionCollection.COPY);
    does.add(() -> this.hold(s, false));
    if (SectionCollection.clip != null) {
      items.add(SectionCollection.PASTE);
      does.add(() -> this.pasteInto(s.group));
    }
    items.add(SectionCollection.SEND);
    does.add(() -> this.sendMenu(s, false));
    items.add(SectionCollection.SEND_GROUP);
    does.add(() -> this.sendMenu(s, true));
    items.add(SectionCollection.DELETE);
    does.add(() -> this.confirm(SectionCollection.deleteAsk(s), () -> {
      try {
        this.vault().deleteSogniSection(s.id);
      } catch (Exception ex) {
        this.say("Could not delete the section: " + ex.getMessage());
      }
      this.refresh();
    }));
    items.add(SectionCollection.MOVE + "\u2026");
    does.add(() -> this.moveMenu(s));
    items.add(SectionCollection.EDIT_NAME);
    does.add(() -> this.editData(s));
    this.menu(SogniSections.label(s), items, does);
  }

  /** Cut or Copy: the section held for Paste, its text on the clipboard too. */
  void hold(PromptVault.SogniSection s, boolean cut) {
    String said = SectionCollection.hold(s, cut);
    try {
      android.content.ClipboardManager cm = (android.content.ClipboardManager) this.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
      if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("section", s.text == null ? "" : s.text.trim()));
    } catch (Exception ignored) {
      // held for Paste all the same
    }
    this.say(said);
  }

  /** Paste: the section cut or copied into this program's group ("" none). */
  void pasteInto(String group) {
    try {
      this.say(SectionCollection.paste(this.vault(), this.app, group));
    } catch (Exception ex) {
      this.say("Could not paste: " + ex.getMessage());
    }
    this.refresh();
  }

  /** Send to other program (`toGroup`: then one of its groups): the program picked, then its group. */
  void sendMenu(final PromptVault.SogniSection s, final boolean toGroup) {
    final List<String> programs;
    try {
      programs = SectionCollection.programs(this.vault(), this.app);
    } catch (Exception ex) {
      this.say("Could not read the programs: " + ex.getMessage());
      return;
    }
    List<Runnable> does = new java.util.ArrayList<Runnable>();
    for (final String p : programs) does.add(() -> {
      if (!toGroup) {
        this.sendTo(s, p, "");
        return;
      }
      List<String> groups = new java.util.ArrayList<String>();
      List<Runnable> into = new java.util.ArrayList<Runnable>();
      try {
        for (final PromptVault.SectionGroup g : this.vault().sectionGroups(p)) {
          groups.add(g.name);
          into.add(() -> this.sendTo(s, p, g.name));
        }
      } catch (Exception ignored) {
        // none listed
      }
      if (groups.isEmpty()) {
        this.say(p + " has no groups yet (its Section Collection's Add new group makes one)");
        return;
      }
      this.menu(SectionCollection.PICK_GROUP + " (" + p + ")", groups, into);
    });
    this.menu(SectionCollection.PICK_PROGRAM, programs, does);
  }

  void sendTo(PromptVault.SogniSection s, String program, String group) {
    try {
      this.say(SectionCollection.send(this.vault(), s, program, group));
    } catch (Exception ex) {
      this.say("Could not send it: " + ex.getMessage());
    }
  }

  /** Move into group: the groups (not its own), and Take out of the group when it is in one. */
  void moveMenu(final PromptVault.SogniSection s) {
    List<String> items = new java.util.ArrayList<String>();
    List<Runnable> does = new java.util.ArrayList<Runnable>();
    try {
      for (final PromptVault.SectionGroup g : this.vault().sectionGroups(this.app)) {
        if (SectionCollection.same(g.name, s.group)) continue;
        items.add(g.name);
        does.add(() -> this.moveTo(s, g.name));
      }
    } catch (Exception ignored) {
      // no groups listed
    }
    if (s.group != null && !s.group.trim().isEmpty()) {
      items.add(SectionCollection.OUT_OF_GROUP);
      does.add(() -> this.moveTo(s, ""));
    }
    if (items.isEmpty()) {
      this.say(SectionCollection.NO_GROUPS);
      return;
    }
    this.menu(SectionCollection.MOVE, items, does);
  }

  void groupMenu(View at, final SectionCollection.Group g) {
    List<String> items = new java.util.ArrayList<String>();
    List<Runnable> does = new java.util.ArrayList<Runnable>();
    items.add(SectionCollection.DELETE);
    does.add(() -> this.confirm(SectionCollection.deleteGroupAsk(g), () -> {
      try {
        this.vault().deleteSectionGroup(this.app, g.name);
      } catch (Exception ex) {
        this.say("Could not delete the group: " + ex.getMessage());
      }
      this.refresh();
    }));
    if (SectionCollection.clip != null) {
      items.add(SectionCollection.PASTE);
      does.add(() -> this.pasteInto(g.name));
    }
    items.add(SectionCollection.RENAME);
    does.add(() -> this.ask(SectionCollection.RENAME, SectionCollection.GROUP_NAME, g.name, SectionCollection.DESCRIPTION, g.note, false, "OK", "Cancel", (name, note) -> {
      if (name.trim().isEmpty()) return;
      try {
        if (!name.trim().equalsIgnoreCase(g.name)) {
          if (this.open.remove(g.name.toLowerCase(java.util.Locale.ROOT))) this.open.add(name.trim().toLowerCase(java.util.Locale.ROOT));
          this.vault().renameSectionGroup(this.app, g.name, name);
        }
        this.vault().addSectionGroup(this.app, name, note);
      } catch (Exception ex) {
        this.say("Could not rename the group: " + ex.getMessage());
      }
      this.refresh();
    }));
    this.menu(g.name, items, does);
  }
}
