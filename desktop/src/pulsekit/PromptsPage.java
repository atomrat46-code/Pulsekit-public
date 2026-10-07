package pulsekit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import static pulsekit.Pulsekit.*;

/**
 * The Prompts page, as on the phone: the encrypted prompt library on this computer. Categories
 * (with subcategories) hold prompts; each save keeps a version, and one version can be final. A
 * prompt has a title, model, description, two reference files, a result file and result text,
 * and its prompt text; a prompt under Code has a type (bash, python...), others an AI box. Export
 * writes it as a .prompt sheet; Open in PyJav loads it there with its reference files.
 */
final class PromptsPage {
    final Pulsekit app;

    PromptsPage(Pulsekit app) {
        this.app = app;
    }

    /** The page's column, rebuilt for the list, the editor or the result text. */
    JPanel col;
    PromptVault vault;
    long categoryId;
    long promptId;
    long loadedVersionId;
    boolean resultTextOpen;

    JTextField promptTitle;

    JTextField promptModel;

    JTextArea promptDescription;

    JTextArea promptBody;

    JLabel promptRef1;

    JLabel promptRef2;

    JLabel promptResult;

    JTextArea resultTextArea;

    byte[] ref1Bytes = new byte[0];
    byte[] ref2Bytes = new byte[0];
    byte[] resultBytes = new byte[0];
    String ref1Name = "";
    String ref2Name = "";
    String resultName = "";
    String resultText = "";
    String codeType = "";

    static final String[] TYPES = {"bash", "cmd", "python", "java", "javascript", "typescript", "powershell"};
    static final long MAX = 16L * 1024 * 1024;

    JPanel buildPromptsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        this.col = new JPanel();
        this.col.setOpaque(false);
        this.col.setLayout(new BoxLayout(this.col, BoxLayout.Y_AXIS));
        this.col.setBorder(BorderFactory.createEmptyBorder(4, 4, 16, 4));
        JScrollPane scroll = new JScrollPane(this.col);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        page.add(scroll, BorderLayout.CENTER);
        this.rebuild();
        return page;
    }

    /** Reads the library (again: PyJav or an import may have added to it) and shows where the page is. */
    void rebuild() {
        if (this.col == null) return;
        try {
            this.vault = PromptDb.vault();
            this.moveOldSheet();
            if (this.vault.category(this.categoryId) == null) {
                List<PromptVault.Category> mains = this.vault.mains();
                this.categoryId = mains.isEmpty() ? 0 : mains.get(0).id;
            }
            if (this.promptId != 0 && this.vault.prompt(this.promptId) == null) {
                this.promptId = 0;
                this.loadedVersionId = 0;
            }
        } catch (Exception ex) {
            this.vault = null;
        }
        this.col.removeAll();
        JLabel title = new JLabel(this.resultTextOpen && this.promptId != 0 ? "Result text" : "Prompts");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(FG);
        title.setAlignmentX(0.0f);
        this.col.add(title);
        if (this.vault == null) {
            this.col.add(Box.createVerticalStrut(8));
            this.col.add(this.note("The encrypted library could not be opened."));
        } else if (this.resultTextOpen && this.promptId != 0) {
            this.buildResultText();
        } else if (this.promptId == 0) {
            this.resultTextOpen = false;
            this.col.add(Box.createVerticalStrut(6));
            this.col.add(this.note("Encrypted library on this computer. Each save keeps a version. One version can be final."));
            this.buildList();
        } else {
            this.buildEditor();
        }
        this.col.revalidate();
        this.col.repaint();
    }

    // ------------------------------------------------------------ list

    void buildList() {
        JPanel library = this.row();
        JButton refs = app.action("Ref files", ELEV, FG);
        refs.setName("prompts-ref-files");
        refs.addActionListener(e -> app.promptDb.gallery(false));
        JButton results = app.action("Result files", ELEV, FG);
        results.setName("prompts-result-files");
        results.addActionListener(e -> app.promptDb.gallery(true));
        library.add(refs);
        library.add(Box.createHorizontalStrut(8));
        library.add(results);
        this.col.add(Box.createVerticalStrut(12));
        this.col.add(library);
        this.col.add(this.caption("CATEGORIES"));
        this.categoryBlock(false);
        JPanel catButtons = this.row();
        JButton addCat = app.action("New category", ELEV, FG);
        addCat.setName("prompts-new-category");
        addCat.addActionListener(e -> {
            String name = this.ask("New category", "");
            if (name == null) return;
            try {
                this.categoryId = this.vault.addCategory(name);
                this.rebuild();
            } catch (Exception ex) {
                this.say(ex);
            }
        });
        JButton addSub = app.action("Add subcategory", ELEV, FG);
        addSub.setName("prompts-add-subcategory");
        addSub.addActionListener(e -> this.addSubcategory());
        catButtons.add(addCat);
        catButtons.add(Box.createHorizontalStrut(8));
        catButtons.add(addSub);
        this.col.add(Box.createVerticalStrut(8));
        this.col.add(catButtons);
        this.col.add(this.caption("PROMPTS"));
        List<PromptVault.Prompt> prompts = this.vault.prompts(this.categoryId);
        if (prompts.isEmpty()) this.col.add(this.note("none"));
        for (PromptVault.Prompt item : prompts) {
            PromptVault.Version fin = this.vault.finalVersion(item.id);
            String detail = fin == null || fin.description == null || fin.description.length() == 0 ? "No description" : firstLine(fin.description);
            JPanel line = new JPanel(new BorderLayout(8, 0));
            line.setOpaque(false);
            line.setAlignmentX(0.0f);
            line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
            JButton open = app.action("<html><b>" + html(item.title) + "</b><br><span style='color:#8A8B86'>" + html(detail) + "</span></html>", SURFACE, FG);
            open.setHorizontalAlignment(JButton.LEFT);
            open.setName("prompt:" + item.title);
            final long id = item.id;
            final String name = item.title == null ? "" : item.title;
            open.addActionListener(e -> this.openPrompt(id));
            app.onChipMenu(open, () -> this.promptMenu(id, name));
            JButton menu = app.action("Menu", ELEV, FG);
            menu.setName("prompt-menu:" + item.title);
            menu.addActionListener(e -> this.promptMenu(id, name).show(menu, 0, menu.getHeight()));
            line.add(open, BorderLayout.CENTER);
            line.add(menu, BorderLayout.EAST);
            this.col.add(line);
            this.col.add(Box.createVerticalStrut(8));
        }
        JButton add = app.action("New prompt", FG, BG);
        add.setName("prompts-new-prompt");
        add.setAlignmentX(0.0f);
        add.addActionListener(e -> {
            String name = this.ask("New prompt", "");
            if (name == null) return;
            try {
                long cat = this.categoryId == 0 ? this.vault.categories().get(0).id : this.categoryId;
                this.openPrompt(this.vault.addPrompt(cat, name));
            } catch (Exception ex) {
                this.say(ex);
            }
        });
        this.col.add(Box.createVerticalStrut(4));
        this.col.add(add);
    }

    /** Main categories as chips, and the chosen one's subcategories below. A right click (or ···) renames or deletes. */
    void categoryBlock(boolean assign) {
        long selected = this.categoryId;
        if (assign && this.promptId != 0) {
            PromptVault.Prompt item = this.vault.prompt(this.promptId);
            if (item != null) selected = item.categoryId;
        }
        long mainId = this.vault.mainOf(selected);
        this.col.add(this.chipRow(this.vault.mains(), mainId));
        List<PromptVault.Category> subs = this.vault.children(mainId);
        if (!subs.isEmpty()) {
            this.col.add(Box.createVerticalStrut(6));
            this.col.add(this.chipRow(subs, selected));
        }
    }

    JPanel chipRow(List<PromptVault.Category> categories, long selectedId) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        row.setOpaque(false);
        row.setAlignmentX(0.0f);
        for (PromptVault.Category category : categories) {
            boolean on = category.id == selectedId;
            final long id = category.id;
            final String name = category.name == null ? "" : category.name;
            final boolean sub = category.parentId != 0;
            JButton chip = app.chip(name, on);
            chip.setName("category:" + name);
            chip.addActionListener(e -> {
                this.categoryId = id;
                this.promptId = 0;
                this.rebuild();
            });
            app.onChipMenu(chip, () -> this.categoryMenu(id, name, sub));
            JButton more = app.chip("···", on);
            more.setName("category-menu:" + name);
            more.addActionListener(e -> this.categoryMenu(id, name, sub).show(more, 0, more.getHeight()));
            row.add(chip);
            row.add(more);
        }
        // Wraps as the window narrows: the row is as tall as its chips need.
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(44, ((categories.size() + 3) / 4) * 40)));
        return row;
    }

    JPopupMenu categoryMenu(long id, String name, boolean sub) {
        String kind = sub ? "subcategory" : "category";
        return this.itemMenu(() -> {
            String next = this.ask("Rename " + kind, name);
            if (next == null) return;
            try {
                this.vault.renameCategory(id, next);
                this.rebuild();
            } catch (Exception ex) {
                this.say(ex);
            }
        }, () -> {
            if (!this.confirm("Delete " + kind, "Delete " + name + "? Prompts inside it are deleted too.")) return;
            try {
                long fallback = this.vault.mainOf(id);
                this.vault.deleteCategory(id);
                if (this.vault.category(this.categoryId) == null) {
                    if (this.vault.category(fallback) != null) {
                        this.categoryId = fallback;
                    } else {
                        List<PromptVault.Category> mains = this.vault.mains();
                        this.categoryId = mains.isEmpty() ? 0 : mains.get(0).id;
                    }
                }
                this.rebuild();
            } catch (Exception ex) {
                this.say(ex);
            }
        });
    }

    JPopupMenu promptMenu(long id, String name) {
        return this.itemMenu(() -> {
            String next = this.ask("Rename prompt", name);
            if (next == null) return;
            try {
                this.vault.renamePrompt(id, next);
                String clean = next.replace('\n', ' ').replace('\r', ' ').trim();
                if (this.promptId == id && this.promptTitle != null) {
                    this.promptTitle.setText(clean);
                    app.setNow("Renamed · " + clean);
                } else {
                    this.rebuild();
                }
            } catch (Exception ex) {
                this.say(ex);
            }
        }, () -> {
            if (!this.confirm("Delete prompt", "Delete " + name + " and its versions?")) return;
            try {
                this.vault.deletePrompt(id);
                if (this.promptId == id) {
                    this.promptId = 0;
                    this.loadedVersionId = 0;
                }
                this.rebuild();
            } catch (Exception ex) {
                this.say(ex);
            }
        });
    }

    JPopupMenu itemMenu(Runnable rename, Runnable delete) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem r = new JMenuItem("Rename");
        r.addActionListener(e -> rename.run());
        JMenuItem d = new JMenuItem("Delete");
        d.addActionListener(e -> delete.run());
        menu.add(r);
        menu.add(d);
        return menu;
    }

    void addSubcategory() {
        String name = this.ask("Add subcategory", "");
        if (name == null) return;
        try {
            long selected = this.categoryId;
            if (this.promptId != 0) {
                PromptVault.Prompt item = this.vault.prompt(this.promptId);
                if (item != null) selected = item.categoryId;
            }
            this.categoryId = this.vault.addSubcategory(selected, name);
            this.promptId = 0;
            this.rebuild();
        } catch (Exception ex) {
            this.say(ex);
        }
    }

    void openPrompt(long id) {
        this.promptId = id;
        PromptVault.Version version = this.vault.finalVersion(id);
        this.loadedVersionId = version == null ? 0 : version.id;
        this.rebuild();
    }

    void loadVersion(long id) {
        this.loadedVersionId = id;
        this.rebuild();
    }

    // ------------------------------------------------------------ editor

    void buildEditor() {
        PromptVault.Prompt item = this.vault.prompt(this.promptId);
        if (item == null) {
            this.promptId = 0;
            this.buildList();
            return;
        }
        JPanel top = this.row();
        JButton back = app.action("Back", ELEV, FG);
        back.setName("prompts-back");
        back.addActionListener(e -> {
            this.promptId = 0;
            this.rebuild();
        });
        final String promptTitleText = item.title == null ? "" : item.title;
        JButton menu = app.action("Menu", ELEV, FG);
        menu.setName("prompts-editor-menu");
        menu.addActionListener(e -> this.promptMenu(this.promptId, promptTitleText).show(menu, 0, menu.getHeight()));
        top.add(back);
        top.add(Box.createHorizontalStrut(8));
        top.add(menu);
        this.col.add(Box.createVerticalStrut(10));
        this.col.add(top);
        this.col.add(this.caption("CATEGORY"));
        this.categoryBlock(true);
        JButton addSub = app.action("Add subcategory", ELEV, FG);
        addSub.setAlignmentX(0.0f);
        addSub.addActionListener(e -> this.addSubcategory());
        this.col.add(Box.createVerticalStrut(6));
        this.col.add(addSub);
        PromptVault.Version loaded = this.vault.version(this.loadedVersionId);
        boolean ours = loaded != null && loaded.promptId == this.promptId;
        if (ours && loaded.codeType != null && loaded.codeType.length() > 0) this.codeType = loaded.codeType;
        else this.codeType = this.inCode(item.categoryId) ? this.typeFor(item.id) : "";
        this.col.add(this.caption("TYPE"));
        if (this.inCode(item.categoryId)) this.col.add(this.typeRow());
        else this.aiBox();
        this.col.add(this.caption("TITLE"));
        this.promptTitle = this.field();
        this.promptTitle.setName("prompt-title");
        this.promptTitle.setText(item.title);
        this.col.add(this.promptTitle);
        this.col.add(this.caption("MODEL"));
        this.promptModel = this.field();
        this.promptModel.setName("prompt-model");
        this.promptModel.setToolTipText("Krea 2Identity edit 1.2 · MiniMax fast H3");
        this.col.add(this.promptModel);
        this.col.add(this.caption("DESCRIPTION"));
        this.promptDescription = this.area(4);
        this.promptDescription.setName("prompt-description");
        this.col.add(this.scroll(this.promptDescription, 110));
        this.promptRef1 = this.fileName();
        this.promptRef2 = this.fileName();
        this.promptResult = this.fileName();
        this.fileBlock("Reference file 1", this.promptRef1, 1);
        this.fileBlock("Reference file 2", this.promptRef2, 2);
        this.fileBlock("Result file · optional", this.promptResult, 3);
        JButton resultTextBtn = app.action("Result text", ELEV, FG);
        resultTextBtn.setName("prompt-result-text");
        resultTextBtn.setAlignmentX(0.0f);
        resultTextBtn.addActionListener(e -> {
            this.resultTextOpen = true;
            this.rebuild();
        });
        this.col.add(Box.createVerticalStrut(10));
        this.col.add(resultTextBtn);
        this.col.add(this.caption("PROMPT"));
        this.promptBody = this.area(10);
        this.promptBody.setName("prompt-body");
        // Right click or a long press: Select all, Cut, Copy, Paste (as on the phone).
        TextMenu.attach(this.promptBody);
        this.col.add(this.scroll(this.promptBody, 220));
        // The prompt from a text file (an Answer Prompt 1.txt made by Extract prompt): Select file or Browse DB.
        JPanel fromFile = this.row();
        JButton select = app.action("Select file", ELEV, FG);
        select.setName("prompt-body-file");
        select.addActionListener(e -> {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text files", "txt", "text", "md", "prompt"));
            if (chooser.showOpenDialog(app) != javax.swing.JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
            this.promptFrom(chooser.getSelectedFile());
        });
        fromFile.add(select);
        JButton browse = app.action("Browse DB", ELEV, FG);
        browse.setName("prompt-body-db");
        browse.setEnabled(!PromptDb.allFiles(PromptDb.TEXTS).isEmpty());
        browse.addActionListener(e -> {
            // A prompt is a text file: Browse DB starts on T.
            DbFilter.current = "T";
            app.promptDb.browse(PromptDb.TEXTS, (picked, file) -> this.promptFrom(file));
        });
        fromFile.add(Box.createHorizontalStrut(8));
        fromFile.add(browse);
        this.col.add(Box.createVerticalStrut(4));
        this.col.add(fromFile);
        if (ours) {
            this.fill(loaded);
        } else {
            this.promptModel.setText(this.modelFor(this.promptId, ""));
            this.ref1Name = "";
            this.ref2Name = "";
            this.resultName = "";
            this.resultText = "";
            this.ref1Bytes = new byte[0];
            this.ref2Bytes = new byte[0];
            this.resultBytes = new byte[0];
            this.promptRef1.setText("No file selected");
            this.promptRef2.setText("No file selected");
            this.promptResult.setText("No file selected");
        }
        resultTextBtn.setText(this.resultText.length() > 0 ? "Result text · saved" : "Result text");
        JPanel actions = this.row();
        JButton save = app.action("Save as new version", FG, BG);
        save.setName("prompt-save");
        save.addActionListener(e -> this.save());
        JButton export = app.action("Export .prompt", ELEV, FG);
        export.setName("prompt-export");
        export.addActionListener(e -> this.exportCurrent());
        JButton openPy = app.action("Open in PyJav", FG, BG);
        openPy.setName("prompt-open-pyjav");
        openPy.addActionListener(e -> this.openInPyJav());
        actions.add(save);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(export);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(openPy);
        this.col.add(Box.createVerticalStrut(14));
        this.col.add(actions);
        this.col.add(this.caption("VERSIONS"));
        List<PromptVault.Version> rows = this.vault.versions(this.promptId);
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        for (int i = 0; i < rows.size(); i++) {
            PromptVault.Version row = rows.get(i);
            String refs = (empty(row.ref1Name) ? "no file" : row.ref1Name) + " · " + (empty(row.ref2Name) ? "no file" : row.ref2Name);
            String modelLine = empty(row.model) ? "" : "<br>" + html(row.model);
            String resultLabel = empty(row.resultName) ? "" : " · " + row.resultName;
            String typeLabel = empty(row.codeType) ? "" : " · " + row.codeType;
            String head = "v" + (rows.size() - i) + " · " + format.format(new Date(row.created)) + (row.finalVersion ? " · FINAL" : "");
            boolean shown = row.id == this.loadedVersionId;
            JPanel line = new JPanel(new BorderLayout(8, 0));
            line.setOpaque(false);
            line.setAlignmentX(0.0f);
            line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
            JButton info = app.action("<html>" + (row.finalVersion ? "<b>" + html(head) + "</b>" : html(head)) + (shown ? " · shown" : "") + modelLine
                + html(typeLabel) + "<br><span style='color:#8A8B86'>" + html(refs + resultLabel) + "</span></html>", shown ? ELEV : SURFACE, FG);
            info.setHorizontalAlignment(JButton.LEFT);
            info.setName("version:v" + (rows.size() - i));
            final long id = row.id;
            info.addActionListener(e -> this.loadVersion(id));
            line.add(info, BorderLayout.CENTER);
            if (!row.finalVersion) {
                JButton mark = app.action("Mark final", FG, BG);
                mark.setName("version-final:v" + (rows.size() - i));
                mark.addActionListener(e -> {
                    try {
                        this.vault.markFinal(id);
                        app.setNow("Marked final");
                        this.rebuild();
                    } catch (Exception ex) {
                        this.say(ex);
                    }
                });
                line.add(mark, BorderLayout.EAST);
            }
            this.col.add(line);
            this.col.add(Box.createVerticalStrut(8));
        }
    }

    void fill(PromptVault.Version version) {
        this.promptDescription.setText(version.description);
        this.promptBody.setText(version.body);
        this.promptBody.setCaretPosition(0);
        this.promptModel.setText(this.modelFor(version.promptId, version.model));
        this.ref1Name = version.ref1Name == null ? "" : version.ref1Name;
        this.ref2Name = version.ref2Name == null ? "" : version.ref2Name;
        this.ref1Bytes = version.ref1 == null ? new byte[0] : version.ref1;
        this.ref2Bytes = version.ref2 == null ? new byte[0] : version.ref2;
        this.resultName = version.resultName == null ? "" : version.resultName;
        this.resultBytes = version.result == null ? new byte[0] : version.result;
        this.resultText = version.resultText == null ? "" : version.resultText;
        this.promptRef1.setText(storedLabel(this.ref1Name, this.ref1Bytes));
        this.promptRef2.setText(storedLabel(this.ref2Name, this.ref2Bytes));
        this.promptResult.setText(storedLabel(this.resultName, this.resultBytes));
        this.codeType = version.codeType == null ? "" : version.codeType;
        if (this.codeType.length() == 0) this.codeType = this.typeFor(version.promptId);
    }

    /** A file row: its name, Choose file, DB (a file already in the library), Menu (rename, delete) and Preview. */
    void fileBlock(String heading, JLabel name, int which) {
        this.col.add(this.caption(heading.toUpperCase()));
        this.col.add(name);
        JPanel actions = this.row();
        JButton pick = app.action("Choose file", ELEV, FG);
        pick.setName("prompt-choose:" + which);
        pick.addActionListener(e -> this.chooseFile(which));
        actions.add(pick);
        boolean any = which == 3 ? !this.vault.resultFiles().isEmpty() : !this.vault.referenceFiles().isEmpty();
        if (any) {
            JButton db = app.action("DB", FG, BG);
            db.setName("prompt-db:" + which);
            db.addActionListener(e -> app.promptDb.pickStored(slotLabel(which), which == 3, (file, card) -> this.useStored(file, which)));
            actions.add(Box.createHorizontalStrut(8));
            actions.add(db);
        }
        JButton menu = app.action("Menu", ELEV, FG);
        menu.setName("prompt-slot-menu:" + which);
        menu.addActionListener(e -> {
            JPopupMenu m = this.slotMenu(which);
            if (m != null) m.show(menu, 0, menu.getHeight());
        });
        JButton preview = app.action("Preview", ELEV, FG);
        preview.setName("prompt-preview:" + which);
        preview.addActionListener(e -> {
            String n = which == 1 ? this.ref1Name : which == 2 ? this.ref2Name : this.resultName;
            byte[] b = which == 1 ? this.ref1Bytes : which == 2 ? this.ref2Bytes : this.resultBytes;
            app.promptDb.preview(n, b);
        });
        actions.add(Box.createHorizontalStrut(8));
        actions.add(menu);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(preview);
        this.col.add(Box.createVerticalStrut(4));
        this.col.add(actions);
    }

    /** A text file's contents into the Prompt field (a prompt sheet gives its prompt). */
    void promptFrom(java.io.File file) {
        try {
            String text = ProgramParams.promptText(java.nio.file.Files.readAllBytes(file.toPath()));
            if (text.length() == 0) {
                app.setNow(file.getName() + " has no text");
                return;
            }
            this.promptBody.setText(text);
            this.promptBody.setCaretPosition(0);
        } catch (Exception ex) {
            app.setNow("Could not read " + file.getName());
        }
    }

    static String slotLabel(int which) {
        return which == 1 ? "Reference file 1" : which == 2 ? "Reference file 2" : "Result file";
    }

    void chooseFile(int which) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(slotLabel(which));
        if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
        this.takeFile(which, chooser.getSelectedFile());
    }

    /** A file from disk into the row, stored in the library at once (in the version shown, or a new one). */
    void takeFile(int which, File file) {
        if (file.length() > MAX) {
            app.setNow(slotLabel(which) + " is too large (max 16 MB)");
            return;
        }
        try {
            this.setSlot(which, file.getName(), Files.readAllBytes(file.toPath()));
            this.storePickedFile(which);
        } catch (Exception ex) {
            this.say(ex);
        }
    }

    void useStored(PromptVault.StoredFile file, int which) {
        byte[] bytes = this.vault.fileBytes(file.versionId, file.which);
        if (bytes == null || bytes.length == 0) {
            app.setNow("That file is empty");
            return;
        }
        this.setSlot(which, file.name, bytes);
        this.storePickedFile(which);
        this.rebuild();
    }

    void setSlot(int which, String name, byte[] bytes) {
        if (which == 1) {
            this.ref1Bytes = bytes;
            this.ref1Name = name;
        } else if (which == 2) {
            this.ref2Bytes = bytes;
            this.ref2Name = name;
        } else {
            this.resultBytes = bytes;
            this.resultName = name;
        }
    }

    void storePickedFile(int which) {
        try {
            if (this.loadedVersionId == 0 || this.vault.version(this.loadedVersionId) == null) {
                this.loadedVersionId = this.vault.addVersion(this.promptId, this.titleText(), text(this.promptDescription), text(this.promptBody), text(this.promptModel),
                    this.ref1Name, this.ref1Bytes, this.ref2Name, this.ref2Bytes, this.resultName, this.resultBytes, this.codeType, this.resultText);
            } else {
                byte[] bytes = which == 1 ? this.ref1Bytes : which == 2 ? this.ref2Bytes : this.resultBytes;
                String name = which == 1 ? this.ref1Name : which == 2 ? this.ref2Name : this.resultName;
                this.vault.putFile(this.loadedVersionId, which, name, bytes);
            }
            app.setNow(slotLabel(which) + " added to the encrypted library");
            JLabel shown = which == 1 ? this.promptRef1 : which == 2 ? this.promptRef2 : this.promptResult;
            String shownName = which == 1 ? this.ref1Name : which == 2 ? this.ref2Name : this.resultName;
            if (shown != null) shown.setText(shownName.length() == 0 ? "No file selected" : shownName + " · encrypted");
        } catch (Exception ex) {
            this.say(ex);
        }
    }

    /** Rename or delete the row's file (in the version shown). Null, after saying so, when the row has none. */
    JPopupMenu slotMenu(int which) {
        String name = which == 1 ? this.ref1Name : which == 2 ? this.ref2Name : this.resultName;
        byte[] bytes = which == 1 ? this.ref1Bytes : which == 2 ? this.ref2Bytes : this.resultBytes;
        if (name == null || name.length() == 0 || bytes == null || bytes.length == 0) {
            app.setNow("No file selected");
            return null;
        }
        String kind = which == 3 ? "result file" : "reference file";
        JLabel label = which == 1 ? this.promptRef1 : which == 2 ? this.promptRef2 : this.promptResult;
        return this.itemMenu(() -> {
            String next = this.ask("Rename " + kind, name);
            if (next == null) return;
            try {
                String clean = this.loadedVersionId != 0 ? this.vault.renameFile(this.loadedVersionId, which, next) : PromptVault.fileTitle(next);
                this.setSlot(which, clean, bytes);
                label.setText(storedLabel(clean, bytes));
                app.setNow("Renamed · " + clean);
            } catch (Exception ex) {
                this.say(ex);
            }
        }, () -> {
            if (!this.confirm("Delete " + kind, "Delete " + name + "?")) return;
            try {
                if (this.loadedVersionId != 0) this.vault.deleteFile(this.loadedVersionId, which);
                this.setSlot(which, "", new byte[0]);
                label.setText("No file selected");
                app.setNow("Deleted · " + name);
            } catch (Exception ex) {
                this.say(ex);
            }
        });
    }

    void save() {
        try {
            this.loadedVersionId = this.vault.addVersion(this.promptId, text(this.promptTitle), text(this.promptDescription), text(this.promptBody), text(this.promptModel),
                this.ref1Name, this.ref1Bytes, this.ref2Name, this.ref2Bytes, this.resultName, this.resultBytes, this.codeType, this.resultText);
            app.setNow("Saved a new version");
            this.rebuild();
        } catch (Exception ex) {
            this.say(ex);
        }
    }

    String titleText() {
        String t = text(this.promptTitle).replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() > 0) return t;
        PromptVault.Prompt item = this.vault.prompt(this.promptId);
        return item != null && item.title != null ? item.title : "Untitled";
    }

    // ------------------------------------------------------------ type, model

    boolean inCode(long id) {
        PromptVault.Category category = this.vault.category(id);
        if (category == null || category.name == null) return false;
        if ("code".equalsIgnoreCase(category.name) && category.parentId == 0) return true;
        if (category.parentId == 0) return false;
        PromptVault.Category parent = this.vault.category(category.parentId);
        return parent != null && parent.name != null && "code".equalsIgnoreCase(parent.name);
    }

    String typeFor(long id) {
        PromptVault.Prompt item = this.vault.prompt(id);
        if (item == null || !this.inCode(item.categoryId)) return "";
        PromptVault.Category category = this.vault.category(item.categoryId);
        String named = category == null ? "" : PromptRun.normalizeType(category.name);
        return named.length() == 0 ? "bash" : named;
    }

    JPanel typeRow() {
        if (this.codeType.length() == 0) this.codeType = this.typeFor(this.promptId);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        row.setOpaque(false);
        row.setAlignmentX(0.0f);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        for (final String type : TYPES) {
            JButton chip = app.chip(type, type.equals(this.codeType));
            chip.setName("prompt-type:" + type);
            chip.addActionListener(e -> {
                this.codeType = type;
                for (java.awt.Component c : row.getComponents()) {
                    if (c instanceof JButton) app.paintChip((JButton) c, type.equals(((JButton) c).getText()));
                }
                try {
                    if (this.loadedVersionId != 0) this.vault.putCodeType(this.loadedVersionId, type);
                    app.setNow("Type · " + type);
                } catch (Exception ex) {
                    this.say(ex);
                }
            });
            row.add(chip);
        }
        return row;
    }

    void aiBox() {
        JCheckBox check = new JCheckBox("AI", "ai".equals(this.codeType));
        check.setName("prompt-ai");
        check.setOpaque(false);
        check.setForeground(FG);
        check.setAlignmentX(0.0f);
        check.addActionListener(e -> {
            this.codeType = check.isSelected() ? "ai" : "";
            try {
                if (this.loadedVersionId != 0) this.vault.putCodeType(this.loadedVersionId, this.codeType);
                app.setNow(check.isSelected() ? "AI · PyJav runs it with Grok, Sogni or Claude" : "AI off");
            } catch (Exception ex) {
                this.say(ex);
            }
        });
        this.col.add(check);
        this.col.add(this.note("Checked means PyJav runs it as an AI prompt (Grok, Sogni or Claude, when found)."));
    }

    String modelFor(long id, String saved) {
        String current = saved == null ? "" : saved.trim();
        String suggested = "";
        PromptVault.Prompt item = this.vault.prompt(id);
        if (item != null) {
            PromptVault.Category cat = this.vault.category(item.categoryId);
            String name = cat == null || cat.name == null ? "" : cat.name;
            if ("image".equalsIgnoreCase(name)) suggested = "Krea 2Identity edit 1.2";
            else if ("video".equalsIgnoreCase(name)) suggested = "MiniMax fast H3";
        }
        if (current.length() == 0) return suggested;
        if (suggested.length() > 0 && !current.equals(suggested)
            && ("Krea 2Identity edit 1.2".equals(current) || "MiniMax fast H3".equals(current))) {
            return suggested;
        }
        return current;
    }

    // ------------------------------------------------------------ result text

    void buildResultText() {
        JButton back = app.action("Back", ELEV, FG);
        back.setName("result-text-back");
        back.setAlignmentX(0.0f);
        back.addActionListener(e -> {
            this.resultTextOpen = false;
            this.rebuild();
        });
        this.col.add(Box.createVerticalStrut(10));
        this.col.add(back);
        this.col.add(Box.createVerticalStrut(10));
        this.resultTextArea = this.area(18);
        this.resultTextArea.setName("result-text");
        this.resultTextArea.setText(this.resultText);
        this.resultTextArea.setToolTipText("Paste the result here");
        this.col.add(this.scroll(this.resultTextArea, 380));
        JPanel actions = this.row();
        JButton paste = app.action("Paste", ELEV, FG);
        paste.addActionListener(e -> this.resultTextArea.paste());
        JButton save = app.action("Save", FG, BG);
        save.setName("result-text-save");
        save.addActionListener(e -> this.saveResultText(this.resultTextArea.getText()));
        actions.add(paste);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(save);
        this.col.add(Box.createVerticalStrut(10));
        this.col.add(actions);
    }

    void saveResultText(String text) {
        try {
            String next = text == null ? "" : text;
            if (next.length() > 1000000) {
                app.setNow("Result text is too large");
                return;
            }
            this.resultText = next;
            if (this.loadedVersionId == 0 || this.vault.version(this.loadedVersionId) == null) {
                this.loadedVersionId = this.vault.addVersion(this.promptId, this.titleText(), text(this.promptDescription), text(this.promptBody), text(this.promptModel), this.ref1Name, this.ref1Bytes,
                    this.ref2Name, this.ref2Bytes, this.resultName, this.resultBytes, this.codeType, this.resultText);
            } else {
                this.vault.putResultText(this.loadedVersionId, this.resultText);
            }
            app.setNow("Result text saved");
        } catch (Exception ex) {
            this.say(ex);
        }
    }

    // ------------------------------------------------------------ export, PyJav

    /** The shown prompt as a .prompt sheet: {file name, text}; null when it has no name. */
    String[] currentSheet() {
        String name = text(this.promptTitle).replace('\n', ' ').replace('\r', ' ').trim();
        String fileName = PromptRun.fileName(name);
        if (fileName.length() == 0) return null;
        PromptVault.Prompt item = this.vault.prompt(this.promptId);
        String category = "";
        if (item != null) {
            PromptVault.Category cat = this.vault.category(item.categoryId);
            if (cat != null && cat.name != null) category = cat.name;
        }
        String version = "unsaved";
        List<PromptVault.Version> rows = this.vault.versions(this.promptId);
        for (int i = 0; i < rows.size(); i++) {
            PromptVault.Version ver = rows.get(i);
            if (ver.id != this.loadedVersionId) continue;
            String when = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ver.created));
            version = "v" + (rows.size() - i) + (ver.finalVersion ? " · FINAL" : "") + " · " + when;
            break;
        }
        String modelName = text(this.promptModel).replace('\n', ' ').replace('\r', ' ').trim();
        String desc = text(this.promptDescription).replace("\r\n", "\n").replace('\r', '\n').trim();
        String body = text(this.promptBody).replace("\r\n", "\n").replace('\r', '\n');
        String sheet = PromptRun.encode(name, "", "", this.ref1Name, this.ref2Name, category, version, modelName, this.resultName, this.codeType, body);
        if (desc.length() > 0) {
            String line = "Description: " + desc.replace('\n', ' ').replace('\r', ' ') + "\n";
            int at = sheet.indexOf("\n---\n");
            if (at >= 0) sheet = sheet.substring(0, at + 1) + line + sheet.substring(at + 1);
        }
        return new String[] {fileName, sheet};
    }

    void exportCurrent() {
        String[] sheet = this.currentSheet();
        if (sheet == null) {
            app.setNow("Name the prompt");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export prompt");
        chooser.setSelectedFile(new File(sheet[0]));
        if (chooser.showSaveDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            app.setNow("Not exported");
            return;
        }
        this.exportTo(chooser.getSelectedFile());
    }

    void exportTo(File dest) {
        String[] sheet = this.currentSheet();
        if (sheet == null) {
            app.setNow("Name the prompt");
            return;
        }
        try {
            if (!dest.getName().toLowerCase().endsWith(".prompt")) {
                File parent = dest.getParentFile();
                dest = new File(parent == null ? new File(".") : parent, dest.getName() + ".prompt");
            }
            Files.write(dest.toPath(), sheet[1].getBytes(StandardCharsets.UTF_8));
            app.setNow("Exported · " + dest.getName());
        } catch (Exception ex) {
            this.say(ex);
        }
    }

    /** Loads the shown prompt in PyJav, with its reference files from the library. */
    void openInPyJav() {
        String[] sheet = this.currentSheet();
        if (sheet == null) {
            app.setNow("Name the prompt");
            return;
        }
        app.pyJav.loadProgram(sheet[0], sheet[1].getBytes(StandardCharsets.UTF_8), false);
        app.pyInputPath = null;
        app.pyJav.showPyHint("Run as bash, cmd, or AI.");
        app.pyJav.loadPromptRefs(sheet[1]);
    }

    // ------------------------------------------------------------ the old single sheet

    /**
     * The desktop's earlier Prompts page kept one sheet in ~/.pulsekit/prompts.txt (and its files
     * as prompt-ref-1.*, prompt-ref-2.*). It moves into the library once, as a prompt in its
     * category (General when it named none), and the file is renamed prompts.txt.moved.
     */
    void moveOldSheet() {
        File dir = PromptDb.dir();
        File old = new File(dir, "prompts.txt");
        if (!old.isFile()) return;
        try {
            PromptRun.Sheet sheet = PromptRun.parse(new String(Files.readAllBytes(old.toPath()), StandardCharsets.UTF_8));
            if (sheet != null && (sheet.name.trim().length() > 0 || sheet.body.trim().length() > 0)) {
                String catName = sheet.category == null || sheet.category.trim().isEmpty() ? "General" : sheet.category.trim();
                long catId = 0;
                for (PromptVault.Category c : this.vault.categories()) if (c.name != null && c.name.trim().equalsIgnoreCase(catName)) catId = c.id;
                if (catId == 0) catId = this.vault.addCategory(catName);
                String title = sheet.name.trim().isEmpty() ? "Prompt" : sheet.name.trim();
                long id = this.vault.addPrompt(catId, title);
                byte[] r1 = oldRef(dir, "prompt-ref-1");
                byte[] r2 = oldRef(dir, "prompt-ref-2");
                this.vault.addVersion(id, title, sheet.description == null ? "" : sheet.description, sheet.body, sheet.model == null ? "" : sheet.model,
                    r1 == null ? "" : sheet.ref1, r1, r2 == null ? "" : sheet.ref2, r2, "", null, sheet.type == null ? "" : sheet.type, "");
            }
            old.renameTo(new File(dir, "prompts.txt.moved"));
        } catch (Exception ignored) {
            // left where it is; tried again next time
        }
    }

    static byte[] oldRef(File dir, String stem) {
        File[] all = dir.listFiles();
        if (all == null) return null;
        for (File f : all) {
            if (f.isFile() && f.getName().startsWith(stem + ".") || f.isFile() && f.getName().equals(stem)) {
                try {
                    return f.length() > MAX ? null : Files.readAllBytes(f.toPath());
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ small parts

    JLabel caption(String text) {
        JLabel lab = new JLabel(text);
        lab.setForeground(SUBTLE);
        lab.setFont(new Font("SansSerif", Font.BOLD, 11));
        lab.setAlignmentX(0.0f);
        lab.setBorder(BorderFactory.createEmptyBorder(14, 0, 4, 0));
        return lab;
    }

    JLabel note(String text) {
        JLabel n = new JLabel("<html><body style='width:520px'>" + html(text) + "</body></html>");
        n.setForeground(MUTED);
        n.setAlignmentX(0.0f);
        return n;
    }

    JPanel row() {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(0.0f);
        return row;
    }

    JLabel fileName() {
        JLabel name = new JLabel("No file selected");
        name.setForeground(FG);
        name.setFont(new Font("Monospaced", Font.PLAIN, 13));
        name.setAlignmentX(0.0f);
        return name;
    }

    JTextField field() {
        JTextField field = new JTextField();
        field.setBackground(SURFACE);
        field.setForeground(FG);
        field.setCaretColor(FG);
        field.setFont(new Font("Monospaced", Font.PLAIN, 13));
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setAlignmentX(0.0f);
        return field;
    }

    JTextArea area(int rows) {
        JTextArea area = new JTextArea(rows, 40);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setBackground(SURFACE);
        area.setForeground(FG);
        area.setCaretColor(FG);
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    JScrollPane scroll(JComponent inside, int height) {
        JScrollPane s = new JScrollPane(inside);
        s.setAlignmentX(0.0f);
        s.setPreferredSize(new Dimension(480, height));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, height + 60));
        s.setBorder(BorderFactory.createLineBorder(BORDER));
        return s;
    }

    /** A name, or null when cancelled or left empty. */
    String ask(String heading, String preset) {
        Object answer = JOptionPane.showInputDialog(app, "Name", heading, JOptionPane.PLAIN_MESSAGE, null, null, preset);
        if (answer == null) return null;
        String name = answer.toString().replace('\n', ' ').replace('\r', ' ').trim();
        return name.isEmpty() ? null : name;
    }

    boolean confirm(String heading, String message) {
        return JOptionPane.showOptionDialog(app, message, heading, JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
            new Object[] {"Delete", "Cancel"}, "Cancel") == 0;
    }

    void say(Exception ex) {
        app.setNow(ex.getMessage() != null ? ex.getMessage() : ex.toString());
    }

    static String text(javax.swing.text.JTextComponent c) {
        return c == null || c.getText() == null ? "" : c.getText();
    }

    static boolean empty(String s) {
        return s == null || s.length() == 0;
    }

    static String firstLine(String s) {
        String t = s == null ? "" : s.trim();
        int nl = t.indexOf('\n');
        return nl < 0 ? t : t.substring(0, nl).trim();
    }

    static String html(String s) {
        return (s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String storedLabel(String name, byte[] bytes) {
        if (name == null || name.length() == 0 || bytes == null || bytes.length == 0) return "No file selected";
        return name + " · encrypted";
    }
}
