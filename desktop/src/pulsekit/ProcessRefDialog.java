package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/**
 * Picture ready > Process as a reference: which program (one run recently that takes pictures),
 * which of its recent prompts, and Reference file 1 or 2; Open loads it in PyJav with the picture
 * in that slot, ready to Run. The choices are kept and picked again next time (ProcessAsRef).
 */
final class ProcessRefDialog {
    final Pulsekit app;
    final File picture;
    final List<PyJavRecent.Item> recent;
    final List<String> programs;
    final ProcessAsRef.Choice choice;
    final JDialog dialog;
    final JComboBox<String> program;
    final JComboBox<String> prompt;
    final JRadioButton ref1 = new JRadioButton("Reference file 1");
    final JRadioButton ref2 = new JRadioButton("Reference file 2");
    List<PyJavRecent.Item> runs;
    /** The dialog shown last (for the tests). */
    static ProcessRefDialog last;

    static void open(Pulsekit app, File picture) {
        ProcessRefDialog d = new ProcessRefDialog(app, picture);
        if (d.programs.isEmpty()) {
            JOptionPane.showMessageDialog(app, "No program that takes pictures has been run yet. Run SogniPedit or SogniVideo once (PyJav), and it is listed here.",
                "Process as a reference", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        last = d;
        d.dialog.setVisible(true);
    }

    String source(String name) {
        String bundled = ProgramFiles.bundledSource(name);
        return bundled != null ? bundled : ProcessAsRef.keptSource(name, this.recent);
    }

    private ProcessRefDialog(Pulsekit app, File picture) {
        this.app = app;
        this.picture = picture;
        this.recent = PyJavRecent.load(app.pyJav.pyRecentDir());
        this.programs = ProcessAsRef.programs(this.recent, (name, items) -> this.source(name));
        this.choice = ProcessAsRef.load(PromptDb.dir());
        this.dialog = new JDialog(app, "Process as a reference", true);
        this.dialog.setName("process-ref");
        this.program = new JComboBox<String>(this.programs.toArray(new String[0]));
        this.program.setName("process-ref-program");
        this.prompt = new JComboBox<String>();
        this.prompt.setName("process-ref-prompt");
        this.ref1.setName("process-ref-1");
        this.ref2.setName("process-ref-2");
        ButtonGroup g = new ButtonGroup();
        g.add(this.ref1);
        g.add(this.ref2);
        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
        rows.add(new JLabel(picture.getName()));
        rows.add(new JLabel("Program"));
        rows.add(this.program);
        rows.add(new JLabel("Prompt (its recent runs)"));
        rows.add(this.prompt);
        rows.add(new JLabel("The picture is"));
        JPanel slots = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        slots.add(this.ref1);
        slots.add(this.ref2);
        rows.add(slots);
        JButton open = new JButton("Open");
        open.setName("process-ref-open");
        open.addActionListener(e -> this.go());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> this.dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(open);
        JPanel page = new JPanel(new BorderLayout(0, 8));
        page.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        page.add(rows, BorderLayout.CENTER);
        page.add(buttons, BorderLayout.SOUTH);
        this.dialog.setContentPane(page);
        this.program.addActionListener(e -> this.setProgram((String) this.program.getSelectedItem(), false));
        if (!this.programs.isEmpty()) {
            this.program.setSelectedIndex(ProcessAsRef.programIndex(this.programs, this.choice));
            this.setProgram((String) this.program.getSelectedItem(), true);
        }
        this.dialog.pack();
        this.dialog.setSize(Math.max(520, this.dialog.getWidth()), this.dialog.getHeight());
        this.dialog.setLocationRelativeTo(app);
    }

    void setProgram(String name, boolean kept) {
        if (name == null) return;
        this.runs = PyJavRecent.runsOf(this.recent, name);
        this.prompt.removeAllItems();
        for (PyJavRecent.Item item : this.runs) this.prompt.addItem(ProcessAsRef.promptLabel(item, 90));
        boolean same = kept && name.equals(this.choice.program);
        if (!this.runs.isEmpty()) this.prompt.setSelectedIndex(same ? ProcessAsRef.runIndex(this.runs, this.choice) : 0);
        List<String> s = ProcessAsRef.slots(this.source(name));
        this.ref1.setText("Reference file 1 (" + s.get(0) + ")");
        this.ref2.setVisible(s.size() > 1);
        if (s.size() > 1) this.ref2.setText("Reference file 2 (" + s.get(1) + ")");
        if (same && this.choice.slot == 2 && s.size() > 1) this.ref2.setSelected(true);
        else this.ref1.setSelected(true);
    }

    /** Open: PyJav with the program, the run's settings and the picture in its slot. */
    void go() {
        String name = (String) this.program.getSelectedItem();
        if (name == null) return;
        int i = this.prompt.getSelectedIndex();
        PyJavRecent.Item item = this.runs == null || this.runs.isEmpty() || i < 0 ? null : this.runs.get(i);
        List<String> s = ProcessAsRef.slots(this.source(name));
        int slot = this.ref2.isSelected() && s.size() > 1 ? 2 : 1;
        String extra = ProcessAsRef.withPicture(item == null ? "" : item.extra, s.get(slot - 1), this.picture.getAbsolutePath());
        this.choice.program = name;
        this.choice.extra = item == null ? "" : item.extra;
        this.choice.slot = slot;
        ProcessAsRef.save(PromptDb.dir(), this.choice);
        this.dialog.dispose();
        this.app.showView("py");
        this.app.pyJav.loadRecent(item != null ? item : PyJavRecent.tapped(this.recent, name));
        if (this.app.pyJav.pyExtra != null) this.app.pyJav.pyExtra.setText(extra);
        this.app.setNow(name + " · " + this.picture.getName() + " as Reference file " + slot + ": Run starts it");
    }
}
