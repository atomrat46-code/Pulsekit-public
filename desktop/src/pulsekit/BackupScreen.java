package pulsekit;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/**
 * File > Back up Pulsekit / Restore from backup (AppBackup), as on Android: the parts ticked into a
 * zip (with a password when the prompt library is in it), and back from one; Pulsekit stops after
 * a restore, so nothing it still holds writes over what came back.
 */
final class BackupScreen {
    private BackupScreen() {}

    /** The tests: where the backup goes / which one is restored (null: ask), the password, and no stopping. */
    static java.util.function.Function<String, File> saveTo;
    static File openFrom;
    static char[] testPassword;
    static boolean keepRunning;
    static String lastSaid;

    static void show(final Pulsekit app) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        col.add(new JLabel("<html><body style='width:420px'>" + AppBackup.NOTE + "</body></html>"));
        JCheckBox[] boxes = new JCheckBox[AppBackup.PARTS.length];
        for (int i = 0; i < AppBackup.PARTS.length; i++) {
            boxes[i] = new JCheckBox(AppBackup.LABELS[i], AppBackup.ON[i]);
            boxes[i].setName("backup-part:" + AppBackup.PARTS[i]);
            col.add(boxes[i]);
        }
        JPasswordField pw = new JPasswordField(20), again = new JPasswordField(20);
        pw.setName("backup-password");
        again.setName("backup-password-again");
        col.add(new JLabel("Password (at least " + DbSlots.MIN_PASSWORD + " characters)"));
        col.add(pw);
        col.add(new JLabel("The password again"));
        col.add(again);
        Object[] options = {"Back up", AppBackup.RESTORE, "Cancel"};
        int got = JOptionPane.showOptionDialog(app, col, AppBackup.BACKUP, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (got == 1) {
            restore(app);
            return;
        }
        if (got != 0) return;
        Set<String> parts = new LinkedHashSet<String>();
        for (int i = 0; i < boxes.length; i++) if (boxes[i].isSelected()) parts.add(AppBackup.PARTS[i]);
        char[] a = testPassword != null ? testPassword : pw.getPassword(), b = testPassword != null ? testPassword : again.getPassword();
        if (parts.isEmpty()) {
            said(app, "Tick what to back up");
            return;
        }
        if (parts.contains(AppBackup.DB) || a.length > 0) {
            if (a.length < DbSlots.MIN_PASSWORD) {
                said(app, DbSlots.SHORT_PASSWORD);
                return;
            }
            if (!java.util.Arrays.equals(a, b)) {
                said(app, "The passwords are not the same");
                return;
            }
        }
        File to = saveTo != null ? saveTo.apply(AppBackup.zipName()) : ask(app, AppBackup.zipName(), true);
        if (to == null) return;
        try {
            said(app, AppBackup.backup(PromptDb.dir(), null, parts, a.length == 0 ? null : a, to, tmp(), "desktop " + Looks.version()) + ": saved " + to.getPath());
        } catch (Exception ex) {
            said(app, "Could not back up Pulsekit: " + ex.getMessage());
        }
    }

    static void restore(Pulsekit app) {
        File zip = openFrom != null ? openFrom : ask(app, null, false);
        if (zip == null) return;
        Set<String> inside;
        boolean locked;
        try {
            inside = AppBackup.partsIn(zip);
            locked = AppBackup.needsPassword(zip);
        } catch (Exception ex) {
            said(app, "Could not read " + zip.getName() + ": " + ex.getMessage());
            return;
        }
        if (inside.isEmpty()) {
            said(app, zip.getName() + " is not a Pulsekit backup");
            return;
        }
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(new JLabel("<html><body style='width:420px'>" + AppBackup.RESTORE_NOTE + "</body></html>"));
        java.util.Map<String, JCheckBox> boxes = new java.util.LinkedHashMap<String, JCheckBox>();
        for (String p : inside) {
            JCheckBox b = new JCheckBox(AppBackup.label(p), true);
            b.setName("restore-part:" + p);
            boxes.put(p, b);
            col.add(b);
        }
        JPasswordField pw = new JPasswordField(20);
        pw.setName("restore-password");
        if (locked) {
            col.add(new JLabel("The backup's password"));
            col.add(pw);
        }
        if (JOptionPane.showConfirmDialog(app, col, AppBackup.RESTORE + ": " + zip.getName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        Set<String> parts = new LinkedHashSet<String>();
        for (java.util.Map.Entry<String, JCheckBox> e : boxes.entrySet()) if (e.getValue().isSelected()) parts.add(e.getKey());
        String text;
        try {
            text = AppBackup.restore(zip, locked ? (testPassword != null ? testPassword : pw.getPassword()) : null, parts, PromptDb.dir(), null, tmp());
        } catch (ZipPack.WrongPassword ex) {
            said(app, ZipPack.WRONG_PASSWORD);
            return;
        } catch (Exception ex) {
            said(app, "Could not restore: " + ex.getMessage());
            return;
        }
        said(app, text);
        if (keepRunning) return;
        JOptionPane.showMessageDialog(app, text + ". Pulsekit stops now: start it again.", AppBackup.RESTORE, JOptionPane.PLAIN_MESSAGE);
        // Stopped at once: closing would save what Pulsekit holds in memory over the restored files.
        Runtime.getRuntime().halt(0);
    }

    static File tmp() {
        File t = new File(System.getProperty("java.io.tmpdir"), "pulsekit-backup-" + System.nanoTime());
        t.mkdirs();
        t.deleteOnExit();
        return t;
    }

    static File ask(Pulsekit app, String name, boolean save) {
        JFileChooser fc = new JFileChooser(new File(System.getProperty("user.home", "."), "Downloads"));
        if (name != null) fc.setSelectedFile(new File(fc.getCurrentDirectory(), name));
        int r = save ? fc.showSaveDialog(app) : fc.showOpenDialog(app);
        return r == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
    }

    static void said(Pulsekit app, String text) {
        lastSaid = text;
        app.setNow(text);
    }
}
