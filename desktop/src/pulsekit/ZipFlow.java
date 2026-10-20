package pulsekit;

import java.io.File;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/**
 * Send to zip (the Media browser's files and folders, the Ref / Result / Prompt files) and File >
 * Import of a zip (ZipImport): the questions and the work. `answer`, when set (the tests), answers
 * the password questions in place of the dialogs ("" for none, null to cancel).
 */
final class ZipFlow {
    private ZipFlow() {}

    static java.util.function.Function<String, String> answer;
    /** What the last zip work said (for the tests). */
    static volatile String lastSaid;
    /** No password (as against null: cancelled). */
    static final char[] NONE = new char[0];

    static void said(Pulsekit app, String text) {
        lastSaid = text;
        app.setNow(text);
    }

    /** Send to zip's question: a password typed twice (recommended), none, or null (cancelled). */
    static char[] askPassword(Pulsekit app, String what) {
        if (answer != null) {
            String a = answer.apply(ZipPack.PASSWORD_TITLE + " · " + what);
            return a == null ? null : a.length() == 0 ? NONE : a.toCharArray();
        }
        while (true) {
            JPasswordField pw = new JPasswordField(20);
            pw.setName("zip-password");
            JPasswordField again = new JPasswordField(20);
            again.setName("zip-password-again");
            JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
            panel.add(new JLabel("<html><body style='width:320px'>" + ZipPack.PASSWORD_NOTE + "</body></html>"));
            panel.add(pw);
            panel.add(again);
            Object[] options = {ZipPack.WITH_PASSWORD, ZipPack.NO_PASSWORD, "Cancel"};
            int ans = JOptionPane.showOptionDialog(app, panel, ZipPack.PASSWORD_TITLE + " · " + what, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (ans == 1) return NONE;
            if (ans != 0) return null;
            if (pw.getPassword().length == 0) {
                JOptionPane.showMessageDialog(app, "Type a password, or press " + ZipPack.NO_PASSWORD);
                continue;
            }
            if (!java.util.Arrays.equals(pw.getPassword(), again.getPassword())) {
                JOptionPane.showMessageDialog(app, "The passwords are not the same");
                continue;
            }
            return pw.getPassword();
        }
    }

    /** `name.zip` in `dir`, never over a file that is there ("name (2).zip"). */
    static File unique(File dir, String name) {
        File out = new File(dir, name);
        String stem = name.replaceAll("\\.zip$", "");
        for (int n = 2; out.exists(); n++) out = new File(dir, stem + " (" + n + ").zip");
        return out;
    }

    /** Send to zip in the Media browser: the file, or the folder with its folders, into "<name>.zip" beside it. */
    static String sendFile(Pulsekit app, File f) {
        char[] password = askPassword(app, f.getName());
        if (password == null) return null;
        try {
            List<ZipPack.Source> sources = f.isDirectory() ? ZipPack.folder(f) : java.util.Collections.singletonList(new ZipPack.Source(f.getName(), f));
            if (sources.isEmpty()) return f.getName() + " has no files";
            File out = unique(f.getParentFile(), ZipPack.zipName(f.getName(), f.isDirectory()));
            ZipPack.write(out, sources, password.length == 0 ? null : password);
            return "Zipped " + f.getName() + " (" + sources.size() + (sources.size() == 1 ? " file" : " files") + (password.length > 0 ? ", with a password" : "") + ") into " + out.getName();
        } catch (Exception ex) {
            return "Could not zip " + f.getName() + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    /** Send to zip of a Ref, Result or Prompt file: saved where the save dialog says (its name.zip first). */
    static void sendLibraryFile(Pulsekit app, PromptVault vault, PromptVault.StoredFile file) {
        char[] password = askPassword(app, file.name);
        if (password == null) return;
        String title = PromptVault.fileTitle(file.name);
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(ZipPack.zipName(title, false)));
        if (chooser.showSaveDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
        File out = chooser.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".zip")) out = new File(out.getParentFile(), out.getName() + ".zip");
        try {
            File linked = vault.linkedFile(file.versionId, file.which);
            ZipPack.Source s = linked != null && linked.isFile() ? new ZipPack.Source(title, linked) : new ZipPack.Source(title, vault.fileBytes(file.versionId, file.which));
            ZipPack.write(out, java.util.Collections.singletonList(s), password.length == 0 ? null : password);
            said(app, "Zipped " + file.name + (password.length > 0 ? " with a password" : "") + " into " + out.getName());
        } catch (Exception ex) {
            said(app, "Could not zip " + file.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        }
    }

    // ------------------------------------------------------------ File > Import: a zip

    /** A zip picked in File > Import: its password, then extract it (into a folder, the DB, both) or keep it in the DB. */
    static void importZip(Pulsekit app, File zip) {
        String name = zip.getName();
        List<ZipPack.Entry> entries;
        try {
            entries = ZipPack.list(zip);
        } catch (Exception ex) {
            said(app, "Could not read " + name + ": " + ex.getMessage());
            return;
        }
        char[] password = null;
        if (ZipPack.encrypted(entries)) {
            while (true) {
                String typed;
                if (answer != null) typed = answer.apply(ZipPack.ASK_PASSWORD);
                else {
                    JPasswordField pw = new JPasswordField(20);
                    pw.setName("zip-open-password");
                    JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));
                    panel.add(new JLabel(ZipPack.ASK_PASSWORD), java.awt.BorderLayout.NORTH);
                    panel.add(pw, java.awt.BorderLayout.CENTER);
                    Object[] options = {"Open", "Cancel"};
                    typed = JOptionPane.showOptionDialog(app, panel, name, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]) == 0
                        ? new String(pw.getPassword()) : null;
                }
                if (typed == null) {
                    said(app, "Not imported: " + name);
                    return;
                }
                if (ZipPack.passwordOk(zip, entries, typed.toCharArray())) {
                    password = typed.toCharArray();
                    break;
                }
                said(app, ZipPack.WRONG_PASSWORD + " " + name);
                // The tests answer once: a wrong one ends there.
                if (answer != null) return;
            }
        }
        Object[] yesNo = {"Yes", "No", "Cancel"};
        int extract = JOptionPane.showOptionDialog(app, ZipImport.extractQuestion(name, entries), ZipImport.EXTRACT_TITLE, JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, yesNo, yesNo[0]);
        try {
            if (extract == 1) {
                keepZip(app, zip, name);
                return;
            }
            if (extract != 0) {
                said(app, "Not imported: " + name);
                return;
            }
            Object[] where = ZipImport.WHERE.clone();
            int w = JOptionPane.showOptionDialog(app, "Where do the files of " + name + " go?", ZipImport.WHERE_TITLE, JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, where, where[ZipImport.DB]);
            if (w < 0) {
                said(app, "Not imported: " + name);
                return;
            }
            int which = 1;
            if (w == ZipImport.DB || w == ZipImport.BOTH) {
                Object[] galleries = ZipImport.GALLERY.clone();
                int g = JOptionPane.showOptionDialog(app, "Into Ref files or Result files?", ZipImport.GALLERY_TITLE, JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, galleries, galleries[0]);
                if (g < 0) {
                    said(app, "Not imported: " + name);
                    return;
                }
                which = ZipImport.which(g);
            }
            StringBuilder text = new StringBuilder();
            if (w == ZipImport.FOLDER || w == ZipImport.BOTH) {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Extract " + name + " into");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                    said(app, "Not extracted: " + name);
                    return;
                }
                text.append("Extracted ").append(ZipImport.intoFolder(zip, entries, password, chooser.getSelectedFile()));
            }
            if (w == ZipImport.DB || w == ZipImport.BOTH) {
                if (text.length() > 0) text.append("; ");
                text.append("Imported ").append(ZipImport.intoDb(PromptDb.vault(), zip, entries, password, which, new File(System.getProperty("java.io.tmpdir", "."))));
            }
            said(app, text.toString());
        } catch (Exception ex) {
            said(app, "Could not import " + name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        }
    }

    /** Not extracted: the zip itself into the DB (Ref files); one of 20 MB or more linked after asking, or not imported. */
    static void keepZip(Pulsekit app, File zip, String name) throws Exception {
        boolean link = false;
        if (zip.length() >= PromptVault.LINK_MIN) {
            Object[] options = {ZipImport.LINK, ZipImport.ABORT};
            if (JOptionPane.showOptionDialog(app, ZipImport.tooBig(name, zip.length()), name, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]) != 0) {
                said(app, "Not imported: " + name);
                return;
            }
            link = true;
        }
        said(app, ZipImport.zipIntoDb(PromptDb.vault(), zip, name, link));
    }
}
