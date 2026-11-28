package pulsekit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Section Collection (Browse sections' SC) on the desktop: the program's groups on the left (Add new
 * at the top; each with (n) and + to open its sections; right click or hold: Delete, Rename), the
 * sections in no group on the right (Add new section at the top right; each its text in a box, its name
 * above; right click or hold: Save, Delete, Move into group, Edit name). A section dragged onto a group
 * goes into it; on the left, a group's sections are dragged onto another group. Close at the bottom right.
 */
public final class SectionCollectionWindow {
  private final Component owner;
  private final String app;
  /** The LoRAs the Params have now (Add new section lists them first), or "". */
  private final String lorasNow;
  /** The program's other rows a section keeps (Aspect, Width, Height...), from its Params; may be none. */
  private final List<ProgramParams.Param> extras;
  final JDialog dialog;
  final JPanel left = new JPanel();
  final JPanel right = new JPanel();
  /** The groups open (+ pressed), by name in lower case. */
  final Set<String> open = new HashSet<String>();
  /** The group picked with a click on its name (Import sections from folder puts the sections in it); null: none. */
  String selected;
  /** The tests: Import's folder (else one picked), its group when none is selected (else asked), and what it said. */
  static java.io.File importDir;
  static String importGroup;
  static volatile String importSaid = "";
  /** The menu shown last (the tests). */
  JPopupMenu lastMenu;

  /** The window shown last, and the tests' answers: dialogs' texts in order (null: asked), yes/no. */
  static SectionCollectionWindow last;
  static java.util.Deque<String[]> answers;
  static Boolean sure;

  private SectionCollectionWindow(Component owner, String app, String lorasNow, List<ProgramParams.Param> ps) {
    this.owner = owner;
    this.app = app;
    this.extras = SectionCollection.extraRows(ps);
    this.lorasNow = lorasNow == null ? "" : lorasNow;
    java.awt.Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
    this.dialog = new JDialog(w, SectionCollection.TITLE + " · " + app, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    this.dialog.setName("section-collection");
    this.left.setLayout(new BoxLayout(this.left, BoxLayout.Y_AXIS));
    this.right.setLayout(new BoxLayout(this.right, BoxLayout.Y_AXIS));
    JButton addGroup = new JButton(SectionCollection.ADD_GROUP);
    addGroup.setName("sc-add-group");
    addGroup.addActionListener(e -> this.addGroup());
    JButton addSection = new JButton(SectionCollection.ADD_SECTION);
    addSection.setName("sc-add-section");
    addSection.addActionListener(e -> this.addSection());
    JButton importDirButton = new JButton(SectionCollection.IMPORT);
    importDirButton.setName("sc-import");
    importDirButton.addActionListener(e -> this.importFolder());
    JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    leftTop.add(addGroup);
    leftTop.add(importDirButton);
    JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
    rightTop.add(addSection);
    JPanel leftSide = new JPanel(new BorderLayout());
    leftSide.add(leftTop, BorderLayout.NORTH);
    JPanel leftHold = new JPanel(new BorderLayout());
    leftHold.add(this.left, BorderLayout.NORTH);
    leftSide.add(new JScrollPane(leftHold), BorderLayout.CENTER);
    JPanel rightSide = new JPanel(new BorderLayout());
    rightSide.add(rightTop, BorderLayout.NORTH);
    JPanel rightHold = new JPanel(new BorderLayout());
    rightHold.add(this.right, BorderLayout.NORTH);
    rightSide.add(new JScrollPane(rightHold), BorderLayout.CENTER);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSide, rightSide);
    split.setResizeWeight(0.35);
    split.setDividerLocation(300);
    JButton close = new JButton(SectionCollection.CLOSE);
    close.setName("sc-close");
    close.addActionListener(e -> this.dialog.dispose());
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
    bottom.add(close);
    JPanel content = new JPanel(new BorderLayout());
    content.add(split, BorderLayout.CENTER);
    content.add(bottom, BorderLayout.SOUTH);
    this.dialog.setContentPane(content);
    this.dialog.setSize(new Dimension(900, 600));
    this.dialog.setLocationRelativeTo(owner);
    this.refresh();
  }

  /** Shows the program's Section Collection (until Close). */
  public static void show(Component owner, String app) {
    show(owner, app, "");
  }

  /** Shows it with the LoRAs the Params have now (Add new section's list has them first). */
  public static void show(Component owner, String app, String lorasNow) {
    show(owner, app, lorasNow, null);
  }

  /** Shows it with the Params' LoRAs now and the program's rows (Add new section and Edit data have its other rows). */
  public static void show(Component owner, String app, String lorasNow, List<ProgramParams.Param> ps) {
    SectionCollectionWindow sc = new SectionCollectionWindow(owner, app, lorasNow, ps);
    last = sc;
    sc.dialog.setVisible(true);
  }

  PromptVault vault() throws Exception {
    return PromptDb.vault();
  }

  /**
   * Import sections from folder: a folder picked; its .txt and .prompt files (asked first) added as new
   * sections into the selected group, or the one asked for when none is selected.
   */
  void importFolder() {
    java.io.File dir = importDir;
    if (dir == null) {
      javax.swing.JFileChooser ch = new javax.swing.JFileChooser();
      ch.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
      ch.setDialogTitle(SectionCollection.IMPORT);
      if (ch.showOpenDialog(this.dialog) != javax.swing.JFileChooser.APPROVE_OPTION) return;
      dir = ch.getSelectedFile();
    }
    List<java.io.File> files = SectionCollection.importFiles(dir);
    if (files.isEmpty()) {
      importSaid = "No .txt or .prompt files in " + dir.getName();
      this.say(importSaid);
      return;
    }
    try {
      String group = this.selected;
      if (group == null) {
        List<String> picks = SectionCollection.groupPicks(this.vault(), this.app);
        Object got = importGroup != null ? importGroup
            : JOptionPane.showInputDialog(this.dialog, SectionCollection.IMPORT_GROUP_ASK, SectionCollection.IMPORT, JOptionPane.QUESTION_MESSAGE, null,
                picks.toArray(), picks.get(0));
        if (got == null) return;
        group = String.valueOf(got);
      }
      if (!this.confirm(SectionCollection.importAsk(files.size(), dir.getName(), this.app, group), SectionCollection.IMPORT)) return;
      java.util.Map<String, byte[]> read = new java.util.LinkedHashMap<String, byte[]>();
      for (java.io.File f : files) read.put(f.getName(), java.nio.file.Files.readAllBytes(f.toPath()));
      int[] r = SectionCollection.importAll(this.vault(), this.app, read, group);
      if (!SectionCollection.NO_GROUP_PICK.equals(group)) this.open.add(group.toLowerCase(java.util.Locale.ROOT));
      this.refresh();
      importSaid = SectionCollection.imported(r, group);
      this.say(importSaid);
    } catch (Exception ex) {
      importSaid = "Could not import the sections: " + ex.getMessage();
      this.say(importSaid);
    }
  }

  void say(String text) {
    if (answers == null) JOptionPane.showMessageDialog(this.dialog, text, SectionCollection.TITLE, JOptionPane.PLAIN_MESSAGE);
  }

  /** The two-field dialog (group or section, Edit name): the texts, or null when cancelled. */
  String[] ask(String title, String first, String firstNow, String second, String secondNow, boolean longSecond, String ok, String cancel) {
    if (answers != null) return answers.poll();
    JTextField a = new JTextField(firstNow, 28);
    JTextArea b = new JTextArea(secondNow, longSecond ? 6 : 3, 40);
    b.setLineWrap(true);
    b.setWrapStyleWord(true);
    TextMenu.attach(a);
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.add(new JLabel(first));
    p.add(a);
    p.add(Box.createVerticalStrut(6));
    p.add(new JLabel(second));
    p.add(new JScrollPane(b));
    Object[] options = {ok, cancel};
    int ans = JOptionPane.showOptionDialog(this.dialog, p, title, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
    return ans == 0 ? new String[] {a.getText(), b.getText()} : null;
  }

  boolean confirm(String text, String title) {
    if (sure != null) return sure;
    return JOptionPane.showConfirmDialog(this.dialog, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
  }

  void addGroup() {
    String[] got = this.ask(SectionCollection.ADD_GROUP, SectionCollection.GROUP_NAME, "", SectionCollection.DESCRIPTION, "", false, "OK", "Cancel");
    if (got == null) return;
    try {
      String bad = SectionCollection.addGroup(this.vault(), this.app, got[0], got[1]);
      if (bad != null) this.say(bad);
    } catch (Exception ex) {
      this.say("Could not add the group: " + ex.getMessage());
    }
    this.refresh();
  }

  /** The tests' form: what OK gives for the form shown (null: Cancel). */
  static java.util.function.UnaryOperator<SectionCollection.Form> formAnswer;

  /**
   * The section form (Add new section, Edit data, the Params' Save as new): name, description, prompt,
   * LoRAs (a list), group (a list) and the program's other rows. The form as OK left it, or null.
   */
  static SectionCollection.Form form(Component at, String title, SectionCollection.Form init, List<String> loras, List<String> groups, List<ProgramParams.Param> extras) {
    if (formAnswer != null) return formAnswer.apply(init);
    JTextField name = new JTextField(init.name, 28);
    TextMenu.attach(name);
    JTextArea note = new JTextArea(init.note, 2, 40);
    note.setLineWrap(true);
    note.setWrapStyleWord(true);
    JTextArea prompt = new JTextArea(init.prompt, 5, 40);
    prompt.setLineWrap(true);
    prompt.setWrapStyleWord(true);
    javax.swing.JComboBox<String> lp = new javax.swing.JComboBox<String>(loras.toArray(new String[0]));
    lp.setName("sc-new-loras");
    lp.setSelectedItem(loras.contains(init.loras) ? init.loras : loras.get(0));
    javax.swing.JComboBox<String> gp = new javax.swing.JComboBox<String>(groups.toArray(new String[0]));
    gp.setName("sc-new-group");
    gp.setSelectedItem(groups.contains(init.group) ? init.group : groups.get(0));
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    java.util.List<Object[]> rows = new java.util.ArrayList<Object[]>();
    rows.add(new Object[] {SectionCollection.SECTION_NAME, name});
    rows.add(new Object[] {SectionCollection.DESCRIPTION, new JScrollPane(note)});
    rows.add(new Object[] {SectionCollection.PROMPT, new JScrollPane(prompt)});
    rows.add(new Object[] {SectionCollection.LORAS, lp});
    rows.add(new Object[] {SectionCollection.GROUP, gp});
    // The program's other rows (and any other entry the section has): a field each.
    java.util.LinkedHashMap<String, JTextField> other = new java.util.LinkedHashMap<String, JTextField>();
    java.util.LinkedHashMap<String, String> labels = new java.util.LinkedHashMap<String, String>();
    for (ProgramParams.Param x : SectionCollection.extraRows(extras)) labels.put(x.token, SectionCollection.rowLabel(x));
    for (java.util.Map.Entry<String, String> e : labels.entrySet()) {
      JTextField f = new JTextField(init.extra.containsKey(e.getKey()) ? init.extra.get(e.getKey()) : "", 20);
      f.setName("sc-new-row:" + e.getKey());
      TextMenu.attach(f);
      other.put(e.getKey(), f);
      rows.add(new Object[] {e.getValue(), f});
    }
    for (Object[] row : rows) {
      JLabel l = new JLabel((String) row[0]);
      l.setAlignmentX(0f);
      ((javax.swing.JComponent) row[1]).setAlignmentX(0f);
      p.add(l);
      p.add((Component) row[1]);
      p.add(Box.createVerticalStrut(6));
    }
    JScrollPane sp = new JScrollPane(p);
    sp.setPreferredSize(new Dimension(520, Math.min(620, p.getPreferredSize().height + 20)));
    boolean edit = SectionCollection.EDIT_NAME.equals(title);
    // Edit data: Save, Save as (a new section, the one edited kept) and Exit.
    Object[] options = edit ? new Object[] {SectionCollection.SAVE, SectionCollection.SAVE_AS, SectionCollection.EXIT} : new Object[] {"OK", "Cancel"};
    int ans = JOptionPane.showOptionDialog(at, sp, title, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
    if (ans != 0 && !(edit && ans == 1)) return null;
    SectionCollection.Form f = new SectionCollection.Form();
    f.asNew = edit && ans == 1;
    f.name = name.getText();
    f.note = note.getText();
    f.prompt = prompt.getText();
    f.loras = (String) lp.getSelectedItem();
    f.group = (String) gp.getSelectedItem();
    f.extra.putAll(init.extra);
    for (java.util.Map.Entry<String, JTextField> e : other.entrySet()) f.extra.put(e.getKey(), e.getValue().getText());
    return f;
  }

  /** Add new section: the form, empty (the Params' LoRAs listed first). */
  void addSection() {
    List<String> loras, groups;
    try {
      loras = SectionCollection.recentLoras(this.vault(), this.app, this.lorasNow);
      groups = SectionCollection.groupPicks(this.vault(), this.app);
    } catch (Exception ex) {
      this.say("Could not read the sections: " + ex.getMessage());
      return;
    }
    this.lastLoras = loras;
    this.lastGroups = groups;
    SectionCollection.Form f;
    if (answers != null && formAnswer == null) {
      String[] got = answers.poll();
      if (got == null) return;
      f = new SectionCollection.Form();
      f.name = got[0];
      f.note = got[1];
      f.prompt = got[2];
      f.loras = got[3];
      f.group = got[4];
    } else f = form(this.dialog, SectionCollection.ADD_SECTION, new SectionCollection.Form(), loras, groups, this.extras);
    if (f == null) return;
    try {
      String bad = SectionCollection.add(this.vault(), this.app, f);
      if (bad != null) this.say(bad);
    } catch (Exception ex) {
      this.say("Could not add the section: " + ex.getMessage());
    }
    this.refresh();
  }

  /** Edit data: the section's form, every field (its rows too). */
  void editData(PromptVault.SogniSection s) {
    SectionCollection.Form init = SectionCollection.formOf(s);
    SectionCollection.Form f;
    try {
      if (answers != null && formAnswer == null) {
        String[] got = answers.poll();
        if (got == null) return;
        f = init;
        f.name = got[0];
        f.note = got[1];
      } else f = form(this.dialog, SectionCollection.EDIT_NAME, init, SectionCollection.lorasFor(this.vault(), this.app, this.lorasNow, init),
          SectionCollection.groupPicks(this.vault(), this.app), this.extras);
      if (f == null) return;
      String bad = f.asNew ? SectionCollection.saveAs(this.vault(), s, f) : SectionCollection.update(this.vault(), s, f);
      if (bad != null) this.say(bad);
    } catch (Exception ex) {
      this.say("Could not save the section: " + ex.getMessage());
    }
    this.refresh();
  }

  /** Add new section's lists shown last (the tests). */
  List<String> lastLoras, lastGroups;

  /** Both sides drawn again from the library. */
  void refresh() {
    this.left.removeAll();
    this.right.removeAll();
    List<SectionCollection.Group> groups;
    List<PromptVault.SogniSection> loose;
    try {
      groups = SectionCollection.groups(this.vault(), this.app);
      loose = SectionCollection.loose(this.vault(), this.app);
    } catch (Exception ex) {
      this.left.add(new JLabel("Could not read the sections: " + ex.getMessage()));
      this.left.revalidate();
      return;
    }
    if (groups.isEmpty()) this.left.add(new JLabel(SectionCollection.NO_GROUPS));
    for (SectionCollection.Group g : groups) this.left.add(this.groupRow(g));
    if (loose.isEmpty()) this.right.add(new JLabel(SectionCollection.NO_SECTIONS));
    for (PromptVault.SogniSection s : loose) this.right.add(this.sectionBox(s));
    this.left.revalidate();
    this.left.repaint();
    this.right.revalidate();
    this.right.repaint();
  }

  JPanel groupRow(final SectionCollection.Group g) {
    JPanel row = new JPanel();
    row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
    row.setAlignmentX(0f);
    row.setName("sc-group:" + g.name);
    JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
    head.setName("sc-group:" + g.name);
    head.setAlignmentX(0f);
    final boolean picked = g.name.equalsIgnoreCase(this.selected);
    JLabel name = new JLabel((picked ? "\u25b8 " : "") + SectionCollection.groupLabel(g));
    name.setName("sc-group:" + g.name);
    name.setFont(name.getFont().deriveFont(Font.BOLD));
    if (picked) {
      name.setOpaque(true);
      name.setBackground(javax.swing.UIManager.getColor("List.selectionBackground"));
      name.setForeground(javax.swing.UIManager.getColor("List.selectionForeground"));
    }
    // A click on the name selects the group (again: none); Import sections from folder puts its sections there.
    name.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        SectionCollectionWindow.this.selected = picked ? null : g.name;
        SectionCollectionWindow.this.refresh();
      }
    });
    name.setToolTipText(g.note.isEmpty() ? SectionCollection.DROP_TIP : g.note);
    head.add(name);
    final boolean isOpen = this.open.contains(g.name.toLowerCase(java.util.Locale.ROOT));
    if (!g.sections.isEmpty()) {
      JButton plus = new JButton(isOpen ? SectionCollection.SHUT : SectionCollection.OPEN);
      plus.setName("sc-open:" + g.name);
      plus.setMargin(new java.awt.Insets(0, 6, 0, 6));
      plus.addActionListener(e -> {
        String k = g.name.toLowerCase(java.util.Locale.ROOT);
        if (!this.open.remove(k)) this.open.add(k);
        this.refresh();
      });
      head.add(plus);
    }
    row.add(head);
    if (!g.note.isEmpty()) {
      JLabel note = new JLabel(g.note);
      note.setFont(note.getFont().deriveFont(Font.ITALIC, note.getFont().getSize2D() - 1f));
      note.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 0));
      note.setAlignmentX(0f);
      row.add(note);
    }
    onMenu(name, () -> this.groupMenu(g));
    onMenu(head, () -> this.groupMenu(g));
    if (isOpen) {
      for (final PromptVault.SogniSection s : g.sections) {
        JLabel one = new JLabel(SectionCollection.shortLabel(s));
        one.setName("sc-in:" + s.id);
        one.setBorder(BorderFactory.createEmptyBorder(1, 22, 1, 4));
        one.setAlignmentX(0f);
        one.setToolTipText(s.text);
        this.dragsOnto(one, s);
        onMenu(one, () -> this.sectionMenu(s, null));
        // A click: the section shown fully (its row may be cut short).
        one.setToolTipText(SectionCollection.FULL_TIP);
        one.addMouseListener(new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) SectionCollectionWindow.this.showFull(s);
          }
        });
        row.add(one);
      }
    }
    row.add(Box.createVerticalStrut(4));
    return row;
  }

  JPanel sectionBox(final PromptVault.SogniSection s) {
    JPanel box = new JPanel(new BorderLayout(0, 2));
    box.setName("sc-section:" + s.id);
    box.setBorder(BorderFactory.createEmptyBorder(4, 4, 6, 4));
    box.setAlignmentX(0f);
    JLabel head = new JLabel(SectionCollection.heading(s).isEmpty() ? "☰" : "☰ " + SectionCollection.heading(s));
    head.setName("sc-head:" + s.id);
    head.setFont(head.getFont().deriveFont(Font.BOLD));
    head.setToolTipText(SectionCollection.DROP_TIP + (s.note == null || s.note.isEmpty() ? "" : " · " + s.note));
    final JTextArea text = new JTextArea(s.text == null ? "" : s.text.trim(), 3, 40);
    text.setName("sc-text:" + s.id);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    box.add(head, BorderLayout.NORTH);
    box.add(new JScrollPane(text), BorderLayout.CENTER);
    box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height + 40));
    this.dragsOnto(head, s);
    onMenu(head, () -> this.sectionMenu(s, text));
    onMenu(text, () -> this.sectionMenu(s, text));
    return box;
  }

  /** The full view shown last (the tests). */
  String lastFull;

  /** A section shown fully: its name, description, when it was saved and its entries. */
  void showFull(PromptVault.SogniSection s) {
    this.lastFull = SogniSections.full(s);
    if (answers != null) return;
    JTextArea t = new JTextArea(this.lastFull, Math.min(16, this.lastFull.split("\n").length + 1), 50);
    t.setEditable(false);
    t.setLineWrap(true);
    t.setWrapStyleWord(true);
    JOptionPane.showMessageDialog(this.dialog, new JScrollPane(t), SogniSections.label(s), JOptionPane.PLAIN_MESSAGE);
  }

  /** Right click, or a hold of 0.6 s: the menu. */
  static void onMenu(final Component c, final java.util.function.Supplier<JPopupMenu> menu) {
    java.awt.event.MouseAdapter a = new java.awt.event.MouseAdapter() {
      javax.swing.Timer hold;
      Point at;

      @Override
      public void mousePressed(java.awt.event.MouseEvent e) {
        this.at = e.getPoint();
        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
          menu.get().show(c, e.getX(), e.getY());
          return;
        }
        this.hold = new javax.swing.Timer(600, ev -> menu.get().show(c, this.at.x, this.at.y));
        this.hold.setRepeats(false);
        this.hold.start();
      }

      @Override
      public void mouseReleased(java.awt.event.MouseEvent e) {
        if (this.hold != null) this.hold.stop();
      }

      @Override
      public void mouseDragged(java.awt.event.MouseEvent e) {
        // A drag (onto a group) is not a hold.
        if (this.hold != null) this.hold.stop();
      }
    };
    c.addMouseListener(a);
    c.addMouseMotionListener(a);
  }

  /** Dragging `handle` and letting go over a group puts the section in it. */
  void dragsOnto(final Component handle, final PromptVault.SogniSection s) {
    java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
      boolean moved;

      @Override
      public void mousePressed(java.awt.event.MouseEvent e) {
        this.moved = false;
      }

      @Override
      public void mouseDragged(java.awt.event.MouseEvent e) {
        this.moved = true;
        handle.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
      }

      @Override
      public void mouseReleased(java.awt.event.MouseEvent e) {
        handle.setCursor(java.awt.Cursor.getDefaultCursor());
        if (!this.moved) return;
        Point p = SwingUtilities.convertPoint(handle, e.getPoint(), SectionCollectionWindow.this.dialog.getContentPane());
        Component under = SwingUtilities.getDeepestComponentAt(SectionCollectionWindow.this.dialog.getContentPane(), p.x, p.y);
        String group = groupOf(under);
        if (group != null) SectionCollectionWindow.this.moveTo(s, group);
      }
    };
    handle.addMouseListener(drag);
    handle.addMouseMotionListener(drag);
  }

  /** The group a component is in (a group's row, or one of its sections' rows), or null. */
  static String groupOf(Component c) {
    for (Component k = c; k != null; k = k.getParent()) {
      String n = k.getName();
      if (n != null && n.startsWith("sc-group:")) return n.substring(9);
    }
    return null;
  }

  /** Dragged onto a group, or Move into group: the section in that group ("" takes it out). */
  void moveTo(PromptVault.SogniSection s, String group) {
    try {
      SectionCollection.move(this.vault(), s.id, group);
    } catch (Exception ex) {
      this.say("Could not move the section: " + ex.getMessage());
    }
    this.refresh();
  }

  JPopupMenu sectionMenu(final PromptVault.SogniSection s, final JTextArea text) {
    JPopupMenu m = new JPopupMenu();
    if (text != null) {
      JMenuItem save = new JMenuItem(SectionCollection.SAVE);
      save.setName("sc-save");
      save.addActionListener(e -> {
        try {
          String bad = SectionCollection.save(this.vault(), s, text.getText());
          if (bad != null) this.say(bad);
        } catch (Exception ex) {
          this.say("Could not save the section: " + ex.getMessage());
        }
        this.refresh();
      });
      m.add(save);
    }
    // Cut, Copy (held for Paste; the text on the clipboard too) and Paste (into this section's group).
    for (final boolean cut : new boolean[] {true, false}) {
      JMenuItem hold = new JMenuItem(cut ? SectionCollection.CUT : SectionCollection.COPY);
      hold.setName(cut ? "sc-cut" : "sc-copy");
      hold.addActionListener(e -> {
        String said = SectionCollection.hold(s, cut);
        try {
          java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(s.text == null ? "" : s.text.trim()), null);
        } catch (Exception ignored) {
          // held for Paste all the same
        }
        this.said = said;
      });
      m.add(hold);
    }
    if (SectionCollection.clip != null) {
      JMenuItem paste = new JMenuItem(SectionCollection.PASTE);
      paste.setName("sc-paste");
      paste.addActionListener(e -> this.pasteInto(s.group));
      m.add(paste);
    }
    // Send to other program (or one of its groups): the section copied there.
    JMenu send = new JMenu(SectionCollection.SEND);
    send.setName("sc-send");
    JMenu sendGroup = new JMenu(SectionCollection.SEND_GROUP);
    sendGroup.setName("sc-send-group");
    try {
      for (final String p : SectionCollection.programs(this.vault(), this.app)) {
        JMenuItem to = new JMenuItem(p);
        to.setName("sc-send-to:" + p);
        to.addActionListener(e -> this.sendTo(s, p, ""));
        send.add(to);
        JMenu groups = new JMenu(p);
        groups.setName("sc-send-group-to:" + p);
        for (final PromptVault.SectionGroup g : this.vault().sectionGroups(p)) {
          JMenuItem gi = new JMenuItem(g.name);
          gi.setName("sc-send-into:" + p + ":" + g.name);
          gi.addActionListener(e -> this.sendTo(s, p, g.name));
          groups.add(gi);
        }
        groups.setEnabled(groups.getItemCount() > 0);
        sendGroup.add(groups);
      }
    } catch (Exception ignored) {
      // none listed
    }
    m.add(send);
    m.add(sendGroup);
    JMenuItem delete = new JMenuItem(SectionCollection.DELETE);
    delete.setName("sc-delete");
    delete.addActionListener(e -> {
      if (!this.confirm(SectionCollection.deleteAsk(s), SectionCollection.DELETE)) return;
      try {
        this.vault().deleteSogniSection(s.id);
      } catch (Exception ex) {
        this.say("Could not delete the section: " + ex.getMessage());
      }
      this.refresh();
    });
    m.add(delete);
    JMenu move = new JMenu(SectionCollection.MOVE);
    move.setName("sc-move");
    try {
      for (final PromptVault.SectionGroup g : this.vault().sectionGroups(this.app)) {
        if (SectionCollection.same(g.name, s.group)) continue;
        JMenuItem to = new JMenuItem(g.name);
        to.setName("sc-move-to:" + g.name);
        to.addActionListener(e -> this.moveTo(s, g.name));
        move.add(to);
      }
    } catch (Exception ignored) {
      // no groups listed
    }
    if (s.group != null && !s.group.trim().isEmpty()) {
      JMenuItem out = new JMenuItem(SectionCollection.OUT_OF_GROUP);
      out.setName("sc-move-out");
      out.addActionListener(e -> this.moveTo(s, ""));
      move.add(out);
    }
    move.setEnabled(move.getItemCount() > 0);
    m.add(move);
    JMenuItem edit = new JMenuItem(SectionCollection.EDIT_NAME);
    edit.setName("sc-edit-name");
    edit.addActionListener(e -> this.editData(s));
    m.add(edit);
    MenuCancel.add(m);
    this.lastMenu = m;
    return m;
  }

  /** What Cut, Copy, Paste or Send said last. */
  String said;

  void pasteInto(String group) {
    try {
      this.said = SectionCollection.paste(this.vault(), this.app, group);
    } catch (Exception ex) {
      this.said = "Could not paste: " + ex.getMessage();
    }
    this.refresh();
  }

  void sendTo(PromptVault.SogniSection s, String program, String group) {
    try {
      this.said = SectionCollection.send(this.vault(), s, program, group);
    } catch (Exception ex) {
      this.said = "Could not send it: " + ex.getMessage();
    }
    this.say(this.said);
  }

  JPopupMenu groupMenu(final SectionCollection.Group g) {
    JPopupMenu m = new JPopupMenu();
    JMenuItem delete = new JMenuItem(SectionCollection.DELETE);
    delete.setName("sc-group-delete");
    delete.addActionListener(e -> {
      if (!this.confirm(SectionCollection.deleteGroupAsk(g), SectionCollection.DELETE)) return;
      try {
        this.vault().deleteSectionGroup(this.app, g.name);
      } catch (Exception ex) {
        this.say("Could not delete the group: " + ex.getMessage());
      }
      this.refresh();
    });
    if (SectionCollection.clip != null) {
      JMenuItem paste = new JMenuItem(SectionCollection.PASTE);
      paste.setName("sc-group-paste");
      paste.addActionListener(e -> this.pasteInto(g.name));
      m.add(paste);
    }
    JMenuItem rename = new JMenuItem(SectionCollection.RENAME);
    rename.setName("sc-group-rename");
    rename.addActionListener(e -> {
      String[] got = this.ask(SectionCollection.RENAME, SectionCollection.GROUP_NAME, g.name, SectionCollection.DESCRIPTION, g.note, false, "OK", "Cancel");
      if (got == null || got[0].trim().isEmpty()) return;
      try {
        if (!got[0].trim().equalsIgnoreCase(g.name)) {
          if (this.open.remove(g.name.toLowerCase(java.util.Locale.ROOT))) this.open.add(got[0].trim().toLowerCase(java.util.Locale.ROOT));
          this.vault().renameSectionGroup(this.app, g.name, got[0]);
        }
        this.vault().addSectionGroup(this.app, got[0], got[1]);
      } catch (Exception ex) {
        this.say("Could not rename the group: " + ex.getMessage());
      }
      this.refresh();
    });
    m.add(delete);
    m.add(rename);
    MenuCancel.add(m);
    this.lastMenu = m;
    return m;
  }
}
