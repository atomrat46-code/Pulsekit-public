package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;

/**
 * File > Database settings (DbSlots): Use encrypted DB, how much room each DB takes, the DBs with
 * Use (and Delete for a secondary one), Send db to zip and Import db zip. The zip's password is
 * typed or read from a file; a DB encrypted with another computer's key asks for that key (typed,
 * or its key file). `answer`, when set (the tests), answers the password and key questions.
 */
final class DbSettings {
    private DbSettings() {}

    static java.util.function.Function<String, String> answer;
    /** The dialog shown last, and what the last DB work said (for the tests). */
    static JDialog last;
    static volatile String lastSaid;

    static void said(Pulsekit app, String text) {
        lastSaid = text;
        app.setNow(text);
    }

    /** What an import came to, in a dialog of its own, as on Android. */
    static void told(Pulsekit app, String title, String text) {
        said(app, text);
        javax.swing.JOptionPane.showMessageDialog(app, text, title, javax.swing.JOptionPane.PLAIN_MESSAGE);
    }

    static void show(final Pulsekit app) {
        final File dir = PromptDb.dir();
        final JDialog dialog = new JDialog(app, DbSlots.TITLE, true);
        last = dialog;
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        // Use encrypted DB (moved here from General settings).
        final JCheckBox box = new JCheckBox("Use encrypted DB", PromptVault.encrypted(dir));
        box.setName("settings-encrypted");
        box.addActionListener(e -> GeneralSettings.change(app, box, box.isSelected()));
        col.add(box);
        col.add(GeneralSettings.note("The DB in use is kept encrypted. Unticked, it is copied into an unencrypted DB and used from then on:\n"
            + "opening and saving are quicker. Ticked again, the encrypted DB is brought up to date and the unencrypted one is deleted."));
        col.add(GeneralSettings.headline("Space on disk"));
        final JTextArea sizes = GeneralSettings.note(String.join("\n", DbSlots.sizeLines(dir)));
        sizes.setName("db-sizes");
        col.add(sizes);
        col.add(GeneralSettings.headline("Databases"));
        final JComboBox<String> list = new JComboBox<String>();
        list.setName("db-list");
        final Runnable refresh = () -> {
            list.removeAllItems();
            for (String n : DbSlots.names(dir)) list.addItem(n);
            list.setSelectedItem(DbSlots.active(dir));
            sizes.setText(String.join("\n", DbSlots.sizeLines(dir)));
            box.setSelected(PromptVault.encrypted(dir));
        };
        refresh.run();
        JButton use = new JButton(DbSlots.USE);
        use.setName("db-use");
        use.addActionListener(e -> {
            String n = (String) list.getSelectedItem();
            if (n == null) return;
            try {
                DbSlots.use(dir, n);
                said(app, "Using the DB " + n);
            } catch (Exception ex) {
                said(app, "Could not use " + n + ": " + ex.getMessage());
            }
            refresh.run();
        });
        JButton delete = new JButton(DbSlots.DELETE);
        delete.setName("db-delete");
        delete.addActionListener(e -> {
            String n = (String) list.getSelectedItem();
            if (n == null || DbSlots.MAIN.equals(n) || n.equals(DbSlots.active(dir))) {
                said(app, "Only a secondary DB that is not in use can be deleted");
                return;
            }
            Object[] options = {"Delete", "Cancel"};
            if (JOptionPane.showOptionDialog(dialog, "Delete the DB " + n + " and all its files?", "Delete DB", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[1]) != 0) return;
            said(app, DbSlots.delete(dir, n) ? "Deleted the DB " + n : "Could not delete " + n);
            refresh.run();
        });
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(list);
        row.add(use);
        row.add(delete);
        col.add(row);
        col.add(GeneralSettings.headline("Export and import"));
        JButton send = new JButton(DbSlots.SEND);
        send.setName("db-send-zip");
        send.addActionListener(e -> {
            export(app, dialog);
            refresh.run();
        });
        JButton imp = new JButton(DbSlots.IMPORT);
        imp.setName("db-import-zip");
        imp.addActionListener(e -> {
            importZip(app, dialog, null);
            refresh.run();
        });
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row2.add(send);
        row2.add(imp);
        col.add(row2);
        col.add(GeneralSettings.note("Send db to zip: the DB in use, unencrypted, into a zip with a password (at least 4 characters).\n"
            + "Import db zip: a DB zip as a new secondary DB, or as the main DB; what it links to that is not on this computer is left out."));
        // Settings master password: greyed until Unlock settings is given the password.
        AppSettings.load(dir);
        final java.util.List<javax.swing.JComponent> controls = java.util.Arrays.asList(box, list, use, delete, send, imp);
        JPanel lockRow = GeneralSettings.unlockRow(app, dialog, () -> GeneralSettings.lock(controls));
        if (lockRow != null) col.add(lockRow, 0);
        GeneralSettings.lock(controls);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                AppSettings.relock();
            }
        });
        for (java.awt.Component c : col.getComponents()) ((javax.swing.JComponent) c).setAlignmentX(0f);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new BorderLayout());
        south.add(close, BorderLayout.EAST);
        JPanel all = new JPanel(new BorderLayout(0, 8));
        all.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 16));
        all.add(col, BorderLayout.CENTER);
        all.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(all);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
    }

    /** A password: typed, or read from a text file (its first line); null when cancelled. */
    static char[] askPassword(java.awt.Component parent, String title, String message, int min) {
        if (answer != null) {
            String a = answer.apply(title);
            return a == null ? null : a.toCharArray();
        }
        while (true) {
            final JPasswordField pw = new JPasswordField(24);
            pw.setName("db-password");
            JPanel panel = new JPanel(new BorderLayout(0, 6));
            panel.add(new JLabel("<html><body style='width:360px'>" + message + "</body></html>"), BorderLayout.NORTH);
            panel.add(pw, BorderLayout.CENTER);
            Object[] options = {"OK", "Read from a file…", "Cancel"};
            int ans = JOptionPane.showOptionDialog(parent, panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            char[] got;
            if (ans == 1) {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("The password's file");
                if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) continue;
                try {
                    got = DbSlots.passwordFrom(chooser.getSelectedFile());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent, "Could not read it: " + ex.getMessage());
                    continue;
                }
            } else if (ans == 0) got = pw.getPassword();
            else return null;
            if (got.length >= Math.max(1, min)) return got;
            JOptionPane.showMessageDialog(parent, min > 1 ? DbSlots.SHORT_PASSWORD : "Type the password");
        }
    }

    /** Send db to zip: its password, where it goes, then the zip. */
    static void export(Pulsekit app, java.awt.Component parent) {
        File dir = PromptDb.dir();
        char[] pw = askPassword(parent, DbSlots.SEND, DbSlots.PASSWORD_NOTE, DbSlots.MIN_PASSWORD);
        if (pw == null) return;
        if (pw.length < DbSlots.MIN_PASSWORD) {
            said(app, DbSlots.SHORT_PASSWORD);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(DbSlots.zipName(dir)));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
        File picked = chooser.getSelectedFile();
        final File zip = picked.getName().toLowerCase().endsWith(".zip") ? picked : new File(picked.getParentFile(), picked.getName() + ".zip");
        try {
            told(app, DbSlots.SEND, WorkProgressDialog.run(parent, DbSlots.SEND, 2, () -> DbSlots.export(dir, zip, pw, tmp())));
        } catch (Exception ex) {
            told(app, DbSlots.SEND, "Could not send the DB to a zip: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        }
    }

    static File tmp() {
        return new File(System.getProperty("java.io.tmpdir", "."));
    }

    /** Import db zip: the zip (picked, or `picked`) copied into the temp folder; its password, a key, then where it goes. */
    static void importZip(Pulsekit app, java.awt.Component parent, File picked) {
        File dir = PromptDb.dir();
        File chosen = picked;
        if (chosen == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(DbSlots.IMPORT);
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Pulsekit DB zip", "zip"));
            if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
            chosen = chooser.getSelectedFile();
        }
        String name = chosen.getName();
        File zip = new File(tmp(), "pulsekit-db-import.zip");
        try {
            java.nio.file.Files.copy(chosen.toPath(), zip.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            List<ZipPack.Entry> entries = ZipPack.list(zip);
            char[] pw = null;
            if (ZipPack.encrypted(entries)) {
                while (true) {
                    pw = askPassword(parent, name, ZipPack.ASK_PASSWORD, 1);
                    if (pw == null) {
                        said(app, "Not imported: " + name);
                        return;
                    }
                    if (ZipPack.passwordOk(zip, entries, pw)) break;
                    said(app, ZipPack.WRONG_PASSWORD + " " + name);
                    if (answer != null) return;
                }
            }
            final char[] password = pw;
            DbSlots.Unpacked u = WorkProgressDialog.run(parent, DbSlots.IMPORT + ": " + name, 1, () -> DbSlots.unpack(zip, name, password, tmp()));
            while (u.needsKey) {
                String typed;
                File keyFile = null;
                if (answer != null) typed = answer.apply(DbSlots.KEY_NOTE);
                else {
                    javax.swing.JTextField hex = new javax.swing.JTextField(40);
                    hex.setName("db-key");
                    JPanel panel = new JPanel(new BorderLayout(0, 6));
                    panel.add(new JLabel("<html><body style='width:360px'>" + DbSlots.KEY_NOTE + "</body></html>"), BorderLayout.NORTH);
                    panel.add(hex, BorderLayout.CENTER);
                    Object[] options = {"OK", "Key file…", "Cancel"};
                    int ans = JOptionPane.showOptionDialog(parent, panel, name, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
                    if (ans == 1) {
                        JFileChooser chooser = new JFileChooser();
                        chooser.setDialogTitle("The DB's key file");
                        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) continue;
                        keyFile = chooser.getSelectedFile();
                        typed = null;
                    } else if (ans == 0) typed = hex.getText();
                    else typed = null;
                    if (ans != 0 && ans != 1) {
                        said(app, "Not imported: " + name);
                        return;
                    }
                }
                if (typed == null && keyFile == null) {
                    said(app, "Not imported: " + name);
                    return;
                }
                try {
                    DbSlots.unlock(u, DbSlots.key(typed, keyFile));
                } catch (Exception ex) {
                    said(app, ex.getMessage());
                    if (answer != null) return;
                }
            }
            // Where it goes: the DB in use empty, asked whether it becomes the main DB; else secondary (first) or main.
            boolean main;
            if (DbSlots.activeEmpty(dir)) {
                Object[] yesNo = {"Yes", "No"};
                main = JOptionPane.showOptionDialog(parent, DbSlots.MAKE_MAIN, DbSlots.AS_TITLE, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, yesNo, yesNo[0]) == 0;
            } else {
                Object[] as = {DbSlots.AS[0], DbSlots.AS[1], "Cancel"};
                int ans = JOptionPane.showOptionDialog(parent, "Import " + name + " as:", DbSlots.AS_TITLE, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, as, as[0]);
                if (ans != 0 && ans != 1) {
                    said(app, "Not imported: " + name);
                    return;
                }
                main = ans == 1;
            }
            final boolean encrypt = PromptVault.encrypted(dir), asMain = main;
            final DbSlots.Unpacked placed = u;
            told(app, DbSlots.IMPORT, WorkProgressDialog.run(parent, DbSlots.IMPORT + ": " + name, 2, () -> {
                int gone = DbSlots.prune(placed);
                String links = gone > 0 ? "; " + gone + (gone == 1 ? " link" : " links") + " to files that are not here left out" : "";
                if (asMain) {
                    String kept = DbSlots.makeMain(dir, placed, encrypt);
                    return "The imported DB is the main DB now" + (kept.length() > 0 ? " (the one before is kept as " + kept + ")" : "") + links;
                }
                return "Imported the DB as " + DbSlots.addSecondary(dir, placed, placed.name, encrypt) + " (Use picks it)" + links;
            }));
        } catch (Exception ex) {
            told(app, DbSlots.IMPORT, "Could not import " + name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        } finally {
            zip.delete();
        }
    }
}
