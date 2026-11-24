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

  private SectionCollectionScreen(Activity activity, String app, final Runnable closed) {
    this.activity = activity;
    this.app = app;
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
    SectionCollectionScreen sc = new SectionCollectionScreen(activity, app, closed);
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

  void addSection() {
    this.ask(SectionCollection.ADD_SECTION, SectionCollection.SECTION_NAME, "", SectionCollection.SECTION_TEXT, "", true, "OK", "Cancel", (name, text) -> {
      try {
        String bad = SectionCollection.addSection(this.vault(), this.app, name, text);
        if (bad != null) this.say(bad);
      } catch (Exception ex) {
        this.say("Could not add the section: " + ex.getMessage());
      }
      this.refresh();
    });
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
        LinearLayout one = new LinearLayout(this.activity);
        one.setOrientation(LinearLayout.HORIZONTAL);
        one.setGravity(android.view.Gravity.CENTER_VERTICAL);
        one.setPadding(dp(14), 0, 0, 0);
        TextView handle = this.handle(s);
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
        one.addView(handle);
        one.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        one.addView(more2);
        row.addView(one);
      }
    }
    return row;
  }

  /** A section's ☰: touched, it is dragged (onto a group). */
  TextView handle(final PromptVault.SogniSection s) {
    TextView h = new TextView(this.activity);
    h.setTag("sc-drag:" + s.id);
    h.setText("☰");
    h.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
    h.setPadding(dp(6), dp(4), dp(8), dp(4));
    h.setContentDescription(SectionCollection.DROP_TIP);
    h.setOnTouchListener((v, e) -> {
      if (e.getAction() != android.view.MotionEvent.ACTION_DOWN) return false;
      android.content.ClipData data = android.content.ClipData.newPlainText("section", SectionCollection.dragText(s));
      v.startDragAndDrop(data, new View.DragShadowBuilder(v), null, 0);
      return true;
    });
    return h;
  }

  View sectionBox(final PromptVault.SogniSection s) {
    LinearLayout box = new LinearLayout(this.activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setTag("sc-section:" + s.id);
    box.setPadding(dp(2), dp(4), dp(2), dp(8));
    LinearLayout head = new LinearLayout(this.activity);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(android.view.Gravity.CENTER_VERTICAL);
    head.addView(this.handle(s));
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
    does.add(() -> this.ask(SectionCollection.EDIT_NAME, SectionCollection.NAME, s.name == null ? "" : s.name, SectionCollection.DESCRIPTION, s.note == null ? "" : s.note,
        false, SectionCollection.SAVE, SectionCollection.EXIT, (name, note) -> {
          try {
            this.vault().nameSogniSection(s.id, name, note);
          } catch (Exception ex) {
            this.say("Could not save the name: " + ex.getMessage());
          }
          this.refresh();
        }));
    this.menu(SogniSections.label(s), items, does);
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
