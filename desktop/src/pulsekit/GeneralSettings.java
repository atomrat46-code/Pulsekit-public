package pulsekit;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * File > General settings: "Use encrypted DB". Unticked, the prompt library is copied into an
 * unencrypted one (~/.pulsekit/prompts-plain.vault), which is used from then on; ticked again, the
 * encrypted one is brought up to date and the unencrypted one deleted. The copy runs off the
 * window's thread ("Processing DB, please wait"); the setting is kept by which library is there.
 */
final class GeneralSettings {
    private GeneralSettings() {}

    static void show(final Pulsekit app) {
        final JDialog dialog = new JDialog(app, "General settings", true);
        JPanel col = new JPanel(new BorderLayout(0, 8));
        col.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        final JCheckBox box = new JCheckBox("Use encrypted DB", PromptVault.encrypted(PromptDb.dir()));
        box.setName("settings-encrypted");
        col.add(box, BorderLayout.NORTH);
        JTextArea note = new JTextArea("The prompt library is kept encrypted. Unticked, it is copied into an unencrypted library in ~/.pulsekit\n"
            + "and used from then on: opening and saving are quicker. Ticked again, the encrypted library is brought up to date\n"
            + "and the unencrypted one is deleted.");
        note.setEditable(false);
        note.setOpaque(false);
        col.add(note, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new BorderLayout());
        south.add(close, BorderLayout.EAST);
        col.add(south, BorderLayout.SOUTH);
        box.addActionListener(e -> change(app, box, box.isSelected()));
        dialog.setContentPane(col);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
    }

    /** Copies the library over on its own thread; the box is greyed until it is done, and set back if it fails. */
    static void change(final Pulsekit app, final JCheckBox box, final boolean on) {
        final java.io.File dir = PromptDb.dir();
        if (PromptVault.encrypted(dir) == on) return;
        box.setEnabled(false);
        app.setNow(on ? "Encrypting the prompt library…" : "Copying the prompt library unencrypted…");
        new Thread(() -> {
            String said;
            boolean ok;
            try {
                PromptVault.setEncrypted(dir, on);
                said = on ? "The prompt library is encrypted again; the unencrypted copy is deleted" : "The prompt library is now kept unencrypted";
                ok = true;
            } catch (Throwable ex) {
                said = "Could not change the prompt library: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                ok = false;
            }
            final String shown = said;
            final boolean done = ok;
            SwingUtilities.invokeLater(() -> {
                if (!done) box.setSelected(!on);
                box.setEnabled(true);
                app.setNow(shown);
            });
        }, "pulsekit-db-settings").start();
    }
}
