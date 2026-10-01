package pulsekit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JScrollPane;

import static pulsekit.Pulsekit.*;

/** The Prompts page: named prompts and reference files, saved as .prompt files. */
final class PromptsPage {
    final Pulsekit app;

    PromptsPage(Pulsekit app) {
        this.app = app;
    }

    JTextField promptTitle;

    JTextArea promptBody;

    JTextField promptOut1;

    JTextField promptOut2;

    JLabel promptRef1;

    JLabel promptRef2;

    File promptRef1File;

    File promptRef2File;

    JPanel buildPromptsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(4, 4, 16, 4));
        JLabel title = new JLabel("Prompts");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(FG);
        title.setAlignmentX(0.0f);
        JLabel lead = new JLabel("<html><body style='width:460px'>Name the prompt, then Save exports that name as a .prompt file. Open it on PyJav and Run.</body></html>");
        lead.setForeground(MUTED);
        lead.setAlignmentX(0.0f);
        col.add(title);
        col.add(Box.createVerticalStrut(6));
        col.add(lead);
        col.add(Box.createVerticalStrut(14));
        this.promptTitle = this.promptNameField();
        col.add(this.promptNameRow("PROMPT NAME", this.promptTitle));
        col.add(Box.createVerticalStrut(10));
        this.promptRef1 = new JLabel("No file selected");
        this.promptRef2 = new JLabel("No file selected");
        col.add(this.promptFileRow("Reference file 1", this.promptRef1, 1));
        col.add(Box.createVerticalStrut(10));
        col.add(this.promptFileRow("Reference file 2", this.promptRef2, 2));
        col.add(Box.createVerticalStrut(12));
        JLabel promptLab = new JLabel("PROMPT");
        promptLab.setForeground(SUBTLE);
        promptLab.setFont(new Font("SansSerif", Font.BOLD, 11));
        promptLab.setAlignmentX(0.0f);
        this.promptBody = new JTextArea(12, 40);
        this.promptBody.setLineWrap(true);
        this.promptBody.setWrapStyleWord(true);
        this.promptBody.setFont(new Font("Monospaced", Font.PLAIN, 13));
        this.promptBody.setBackground(SURFACE);
        this.promptBody.setForeground(FG);
        this.promptBody.setCaretColor(FG);
        this.promptBody.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane promptScroll = new JScrollPane(this.promptBody);
        promptScroll.setAlignmentX(0.0f);
        promptScroll.setPreferredSize(new Dimension(480, 220));
        promptScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        promptScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        col.add(promptLab);
        col.add(Box.createVerticalStrut(4));
        col.add(promptScroll);
        col.add(Box.createVerticalStrut(12));
        this.promptOut1 = this.promptNameField();
        this.promptOut2 = this.promptNameField();
        col.add(this.promptNameRow("OUTPUT FILE 1", this.promptOut1));
        col.add(Box.createVerticalStrut(8));
        col.add(this.promptNameRow("OUTPUT FILE 2", this.promptOut2));
        col.add(Box.createVerticalStrut(14));
        JButton save = app.action("Save", FG, BG);
        save.setAlignmentX(0.0f);
        save.addActionListener(e -> this.savePrompts());
        col.add(save);
        this.loadPrompts();
        JScrollPane scroll = new JScrollPane(col);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    JPanel promptFileRow(String label, JLabel name, int which) {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(0.0f);
        JLabel lab = new JLabel(label.toUpperCase());
        lab.setForeground(SUBTLE);
        lab.setFont(new Font("SansSerif", Font.BOLD, 11));
        lab.setAlignmentX(0.0f);
        name.setForeground(FG);
        name.setFont(new Font("Monospaced", Font.PLAIN, 13));
        name.setAlignmentX(0.0f);
        JButton pick = app.action("Choose file", ELEV, FG);
        pick.setAlignmentX(0.0f);
        pick.addActionListener(e -> this.pickPromptFile(which));
        box.add(lab);
        box.add(Box.createVerticalStrut(4));
        box.add(name);
        box.add(Box.createVerticalStrut(6));
        box.add(pick);
        return box;
    }

    JTextField promptNameField() {
        JTextField field = new JTextField();
        field.setBackground(SURFACE);
        field.setForeground(FG);
        field.setCaretColor(FG);
        field.setFont(new Font("Monospaced", Font.PLAIN, 13));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setAlignmentX(0.0f);
        return field;
    }

    JPanel promptNameRow(String label, JTextField field) {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(0.0f);
        JLabel lab = new JLabel(label);
        lab.setForeground(SUBTLE);
        lab.setFont(new Font("SansSerif", Font.BOLD, 11));
        lab.setAlignmentX(0.0f);
        box.add(lab);
        box.add(Box.createVerticalStrut(4));
        box.add(field);
        return box;
    }

    void pickPromptFile(int which) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(app) != 0) return;
        File file = chooser.getSelectedFile();
        if (file == null) return;
        if (file.length() > 16L * 1024L * 1024L) {
            app.setNow((which == 1 ? "Reference file 1" : "Reference file 2") + " is too large (max 16 MB)");
            return;
        }
        if (which == 1) {
            this.promptRef1File = file;
            this.promptRef1.setText(file.getName());
        } else {
            this.promptRef2File = file;
            this.promptRef2.setText(file.getName());
        }
    }

    File promptsFile() {
        File dir = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (!dir.isDirectory()) dir.mkdirs();
        return new File(dir, "prompts.txt");
    }

    void loadPrompts() {
        File file = this.promptsFile();
        if (!file.isFile() || this.promptBody == null) return;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            PromptRun.Sheet sheet = PromptRun.parse(text);
            if (sheet == null) return;
            if (this.promptTitle != null) this.promptTitle.setText(sheet.name);
            this.promptOut1.setText(sheet.out1);
            this.promptOut2.setText(sheet.out2);
            if (sheet.ref1.length() > 0) this.promptRef1.setText(sheet.ref1);
            if (sheet.ref2.length() > 0) this.promptRef2.setText(sheet.ref2);
            this.promptBody.setText(sheet.body);
        } catch (Exception ignored) {
            /* keep the empty sheet */
        }
    }

    void savePrompts() {
        try {
            String name = this.promptTitle == null ? "" : this.promptTitle.getText().replace('\n', ' ').replace('\r', ' ').trim();
            String fileName = PromptRun.fileName(name);
            if (fileName.isEmpty()) {
                app.setNow("Name the prompt");
                return;
            }
            String out1 = this.promptOut1.getText().replace('\n', ' ').replace('\r', ' ');
            String out2 = this.promptOut2.getText().replace('\n', ' ').replace('\r', ' ');
            String ref1 = this.promptRef1.getText();
            String ref2 = this.promptRef2.getText();
            if ("No file selected".equals(ref1)) ref1 = "";
            if ("No file selected".equals(ref2)) ref2 = "";
            String body = this.promptBody.getText() == null ? "" : this.promptBody.getText();
            String text = PromptRun.encode(name, out1, out2, ref1, ref2, body);
            Files.write(this.promptsFile().toPath(), text.getBytes(StandardCharsets.UTF_8));
            this.copyPromptRef(this.promptRef1File, "prompt-ref-1");
            this.copyPromptRef(this.promptRef2File, "prompt-ref-2");
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export prompt");
            chooser.setSelectedFile(new File(fileName));
            if (chooser.showSaveDialog(app) != 0) {
                app.setNow("Not exported");
                return;
            }
            File dest = chooser.getSelectedFile();
            if (dest == null) {
                app.setNow("Not exported");
                return;
            }
            if (!dest.getName().toLowerCase().endsWith(".prompt")) {
                File parent = dest.getParentFile();
                dest = new File(parent == null ? new File(".") : parent, dest.getName() + ".prompt");
            }
            Files.write(dest.toPath(), text.getBytes(StandardCharsets.UTF_8));
            app.setNow("Saved · " + dest.getName());
        } catch (Exception ex) {
            app.setNow(ex.getMessage() != null ? ex.getMessage() : "Could not save");
        }
    }

    void copyPromptRef(File src, String stem) throws Exception {
        if (src == null || !src.isFile()) return;
        String name = src.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot) : "";
        File dest = new File(this.promptsFile().getParentFile(), stem + ext);
        Files.write(dest.toPath(), Files.readAllBytes(src.toPath()));
    }
}
