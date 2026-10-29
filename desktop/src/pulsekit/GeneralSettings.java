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
 * File > General settings: "Ask where to save a prompt after a run" (PromptSave), VLC, Sogni and
 * File types. Use encrypted DB is in Database settings now (DbSettings), its change made here
 * (change): unticked, the prompt library is copied into an
 * unencrypted one (~/.pulsekit/prompts-plain.vault), which is used from then on; ticked again, the
 * encrypted one is brought up to date and the unencrypted one deleted. The copy runs off the
 * window's thread ("Processing DB, please wait"); the setting is kept by which library is there.
 */
final class GeneralSettings {
    private GeneralSettings() {}

    /** Python for scripts' first choice: the one found first, as before. */
    static final String AUTO_PYTHON = "Automatic (python3, python or py, found first)";

    static void show(final Pulsekit app) {
        final JDialog dialog = new JDialog(app, "General settings", true);
        JPanel col = new JPanel(new BorderLayout(0, 8));
        col.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        // After a run with --saveprompt: ask where the prompt goes (Save prompt), or store it where its sheet says.
        PromptSave.load(PromptDb.dir());
        final JCheckBox ask = new JCheckBox("Ask where to save a prompt after a run", PromptSave.ask());
        ask.setName("settings-ask-save");
        ask.addActionListener(e -> PromptSave.setAsk(ask.isSelected()));
        JTextArea askNote = new JTextArea("A run with --saveprompt (Save the prompt into DB) shows Save prompt: main category, subcategory,\n"
            + "name and description; the prompt is saved as its next version, with the date. A prompt the same as one in the\n"
            + "library already is not saved again. Unticked, it goes into the category its sheet names.");
        askNote.setEditable(false);
        askNote.setOpaque(false);
        // VLC plays videos inside Pulsekit; where it does not work, the browser plays them.
        final JCheckBox vlc = new JCheckBox("Use VLC for videos (when it is installed)", !VlcPlayer.turnedOff());
        vlc.setName("settings-vlc");
        vlc.addActionListener(e -> {
            VlcPlayer.setOff(!vlc.isSelected());
            app.setNow(vlc.isSelected() ? "VLC is used again for videos" : "VLC is not used: videos play in the browser, thumbnails without a picture");
        });
        JTextArea vlcNote = new JTextArea("Unticked, Pulsekit does not start VLC at all (when VLC crashes on this computer). If VLC closes\n"
            + "Pulsekit as it starts, it is unticked at the next start by itself.");
        vlcNote.setEditable(false);
        vlcNote.setOpaque(false);
        JPanel vlcBox = new JPanel(new BorderLayout(0, 4));
        vlcBox.add(vlc, BorderLayout.NORTH);
        vlcBox.add(vlcNote, BorderLayout.CENTER);
        JPanel askBox = new JPanel(new BorderLayout(0, 8));
        askBox.add(ask, BorderLayout.NORTH);
        askBox.add(askNote, BorderLayout.CENTER);
        askBox.add(vlcBox, BorderLayout.SOUTH);
        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.add(askBox, BorderLayout.CENTER);
        // Sogni: which Sogni tools PyJav lists. File types: a type unticked is left out of the thumbnails (its letter too) and of the playlists shown.
        AppSettings.load(PromptDb.dir());
        JPanel more = new JPanel();
        more.setLayout(new javax.swing.BoxLayout(more, javax.swing.BoxLayout.Y_AXIS));
        more.add(headline("Sogni"));
        final JCheckBox sogni = new JCheckBox(AppSettings.SHOW_SOGNI, AppSettings.showSogni());
        sogni.setName("settings-show-sogni");
        final JCheckBox music = new JCheckBox(AppSettings.MUSIC_ONLY, AppSettings.musicOnly());
        music.setName("settings-sogni-music-only");
        music.setEnabled(AppSettings.showSogni());
        sogni.addActionListener(e -> {
            boolean on = sogni.isSelected();
            AppSettings.setShowSogni(on);
            if (!on && music.isSelected()) {
                music.setSelected(false);
                AppSettings.setMusicOnly(false);
            }
            music.setEnabled(on);
            app.setNow(on ? "Sogni tools are listed in PyJav" : "No Sogni tools in PyJav's list");
        });
        music.addActionListener(e -> {
            AppSettings.setMusicOnly(music.isSelected());
            app.setNow(music.isSelected() ? "Only SogniMusic and SogniChat are listed of the Sogni tools" : "All Sogni tools are listed in PyJav");
        });
        more.add(sogni);
        more.add(music);
        more.add(note("Unticked, no Sogni program is in PyJav's list (Select program). Only music tools and chat: SogniMusic and\n"
            + "SogniChat are listed, the image and video tools are not."));
        more.add(headline("File types"));
        for (int i = 0; i < AppSettings.TYPE_LETTERS.length; i++) {
            final String letter = AppSettings.TYPE_LETTERS[i];
            final JCheckBox type = new JCheckBox(AppSettings.TYPE_LABELS[i] + " (" + letter + ")", AppSettings.shows(letter));
            type.setName("settings-show:" + letter);
            type.addActionListener(e -> {
                AppSettings.setShows(letter, type.isSelected());
                app.setNow(DbFilter.label(letter) + (type.isSelected() ? " are shown again" : " are left out of the thumbnails and playlists"));
            });
            more.add(type);
        }
        more.add(note("A type unticked is not shown in the thumbnail screens (Browse DB, the Reference, Result and Prompt files, the\n"
            + "Media browser), its letter is not there either, and a playlist with files of that type shows the others only."));
        // Tabs: the DB/Prompts tab shown or not.
        more.add(headline("Tabs"));
        final JCheckBox promptsTab = new JCheckBox(AppSettings.SHOW_PROMPTS_TAB, AppSettings.showPromptsTab());
        promptsTab.setName("settings-show-prompts-tab");
        promptsTab.addActionListener(e -> {
            AppSettings.setShowPromptsTab(promptsTab.isSelected());
            if (!promptsTab.isSelected() && "prompts".equals(app.view)) app.showView("py");
            app.refreshTabs();
            app.setNow(promptsTab.isSelected() ? "The DB/Prompts tab is shown" : "No DB/Prompts tab (File > Import as Ref file and the galleries still reach the DB)");
        });
        more.add(promptsTab);
        // Look: FlatLaf's modern look, or Swing's classic one; Pulsekit starts with it next time.
        more.add(headline("Look"));
        final JCheckBox modernLook = new JCheckBox(AppSettings.MODERN_LOOK, AppSettings.modernLook());
        modernLook.setName("settings-modern-look");
        modernLook.addActionListener(e -> {
            AppSettings.setModernLook(modernLook.isSelected());
            app.setNow(modernLook.isSelected() ? "Modern look: Pulsekit starts with it next time" : "Classic look: Pulsekit starts with it next time");
        });
        more.add(modernLook);
        javax.swing.JLabel lookNow = new javax.swing.JLabel(Looks.inUse());
        lookNow.setName("settings-look-in-use");
        lookNow.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 24, 4, 0));
        more.add(lookNow);
        // Python for scripts: the one PyJav runs .py programs with (a computer may have several, pip installing into one).
        more.add(headline(AppSettings.PYTHON));
        final javax.swing.JComboBox<String> python = new javax.swing.JComboBox<String>(new String[] {AUTO_PYTHON});
        python.setName("settings-python");
        python.setAlignmentX(0f);
        python.setMaximumSize(new java.awt.Dimension(760, python.getPreferredSize().height));
        final java.util.List<String> paths = new java.util.ArrayList<String>();
        paths.add("");
        if (AppSettings.pythonPath().length() > 0) {
            python.addItem(AppSettings.pythonPath());
            paths.add(AppSettings.pythonPath());
            python.setSelectedIndex(1);
        }
        final boolean[] filling = {true};
        python.addActionListener(e -> {
            int i = python.getSelectedIndex();
            if (filling[0] || i < 0 || i >= paths.size()) return;
            AppSettings.setPythonPath(paths.get(i));
            app.setNow(i == 0 ? "Scripts run with the Python found first (python3, python or py)" : "Scripts run with " + python.getSelectedItem());
        });
        more.add(python);
        more.add(note("Searching this computer for Pythons\u2026 Pick the one pip installed numpy, DawDreamer and the rest into (a new Python may have no packages for them yet)."));
        new Thread(() -> {
            final java.util.List<PythonRun.Found> found = PyJav.pythonsFound != null ? PyJav.pythonsFound.get() : PythonRun.found();
            javax.swing.SwingUtilities.invokeLater(() -> {
                String picked = AppSettings.pythonPath();
                for (PythonRun.Found f : found) {
                    if (paths.contains(f.path)) {
                        // The kept one: its label with the version.
                        int at = paths.indexOf(f.path);
                        python.removeItemAt(at);
                        python.insertItemAt(f.label(), at);
                        continue;
                    }
                    paths.add(f.path);
                    python.addItem(f.label());
                }
                python.setSelectedIndex(Math.max(0, paths.indexOf(picked)));
                filling[0] = false;
            });
        }, "pythons").start();
        // Logs: written only while ticked (File > Logs shows them).
        more.add(headline("Logs"));
        final JCheckBox logging = new JCheckBox(Logs.SETTING, AppSettings.logging());
        logging.setName("settings-logging");
        logging.addActionListener(e -> {
            AppSettings.setLogging(logging.isSelected());
            if (logging.isSelected()) Logs.started("Pulsekit " + Looks.version() + " (desktop), logging turned on");
            app.setNow(logging.isSelected() ? "Logging on: File > Logs shows what is written" : "Logging off: no log files are written");
        });
        more.add(logging);
        more.add(note(Logs.SETTING_NOTE.replace("Logs shows", "Logs shows\n")));
        // Security: the settings master password.
        more.add(headline("Security"));
        final JCheckBox master = new JCheckBox(AppSettings.MASTER, AppSettings.masterOn());
        master.setName("settings-master");
        master.addActionListener(e -> {
            if (master.isSelected() == AppSettings.masterOn()) return;
            if (!master.isSelected()) {
                AppSettings.clearMaster();
                app.setNow("Settings master password off: the settings are open");
                return;
            }
            char[] pw = askMaster(dialog);
            if (pw == null) {
                master.setSelected(false);
                return;
            }
            try {
                AppSettings.setMaster(pw);
                app.setNow("Settings master password on: the settings are locked when they close");
            } catch (Exception ex) {
                master.setSelected(false);
                app.setNow(ex.getMessage());
            }
        });
        more.add(master);
        more.add(note(AppSettings.MASTER_NOTE.replace("; unticked", ";\nunticked")));
        for (java.awt.Component c : more.getComponents()) ((javax.swing.JComponent) c).setAlignmentX(0f);
        // Settings master password: everything greyed until Unlock settings is given the password.
        final java.util.List<javax.swing.JComponent> controls = java.util.Arrays.asList(ask, vlc, sogni, music, promptsTab, modernLook, python, logging, master);
        final java.util.List<javax.swing.JComponent> all2 = new java.util.ArrayList<javax.swing.JComponent>(controls);
        for (java.awt.Component c : more.getComponents()) if (c instanceof JCheckBox && !all2.contains(c)) all2.add((JCheckBox) c);
        JPanel lockRow = unlockRow(app, dialog, () -> lock(all2));
        if (lockRow != null) middle.add(lockRow, BorderLayout.NORTH);
        lock(all2);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                AppSettings.relock();
            }
        });
        JPanel all = new JPanel(new BorderLayout(0, 8));
        all.add(middle, BorderLayout.NORTH);
        all.add(more, BorderLayout.CENTER);
        col.add(all, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new BorderLayout());
        south.add(close, BorderLayout.EAST);
        col.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(col);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
    }

    /** The answers the tests give to the master password questions (null: ask). */
    static java.util.function.Function<String, String> answer;

    /** The settings' controls greyed while the master password locks them (Show only music and chat also needs the Sogni tools). */
    static void lock(java.util.List<javax.swing.JComponent> controls) {
        boolean open = !AppSettings.locked();
        for (javax.swing.JComponent c : controls) c.setEnabled(open && (!"settings-sogni-music-only".equals(c.getName()) || AppSettings.showSogni()));
    }

    /** With a master password: a note and Unlock settings…, which asks it and (given right) runs `unlocked`; null without one. */
    static JPanel unlockRow(final Pulsekit app, final java.awt.Component parent, final Runnable unlocked) {
        if (!AppSettings.masterOn()) return null;
        final JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(new javax.swing.JLabel(AppSettings.LOCKED_NOTE), BorderLayout.CENTER);
        final JButton unlock = new JButton(AppSettings.UNLOCK);
        unlock.setName("settings-unlock");
        unlock.addActionListener(e -> {
            String typed;
            if (answer != null) typed = answer.apply(AppSettings.UNLOCK);
            else {
                javax.swing.JPasswordField pw = new javax.swing.JPasswordField(20);
                pw.setName("settings-master-password");
                typed = javax.swing.JOptionPane.showConfirmDialog(parent, pw, "Master password", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    javax.swing.JOptionPane.PLAIN_MESSAGE) == javax.swing.JOptionPane.OK_OPTION ? new String(pw.getPassword()) : null;
            }
            if (typed == null) return;
            if (!AppSettings.unlock(typed.toCharArray())) {
                app.setNow("Wrong master password: the settings stay locked");
                return;
            }
            app.setNow("Settings unlocked");
            unlock.setEnabled(false);
            unlocked.run();
        });
        row.add(unlock, BorderLayout.EAST);
        return row;
    }

    /** Settings master password ticked: the password twice; null when cancelled or not the same. */
    static char[] askMaster(java.awt.Component parent) {
        if (answer != null) {
            String a = answer.apply(AppSettings.MASTER);
            return a == null ? null : a.toCharArray();
        }
        while (true) {
            javax.swing.JPasswordField pw = new javax.swing.JPasswordField(20);
            pw.setName("settings-master-new");
            javax.swing.JPasswordField again = new javax.swing.JPasswordField(20);
            again.setName("settings-master-again");
            JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
            panel.add(new javax.swing.JLabel("The settings are changed only after this password is given. At least " + AppSettings.MASTER_MIN + " characters."));
            panel.add(pw);
            panel.add(again);
            if (javax.swing.JOptionPane.showConfirmDialog(parent, panel, AppSettings.MASTER, javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE) != javax.swing.JOptionPane.OK_OPTION) return null;
            if (java.util.Arrays.equals(pw.getPassword(), again.getPassword())) return pw.getPassword();
            javax.swing.JOptionPane.showMessageDialog(parent, "The passwords are not the same");
        }
    }

    /** A section's headline ("Sogni", "File types"). */
    static javax.swing.JLabel headline(String text) {
        javax.swing.JLabel l = new javax.swing.JLabel(text);
        l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD, l.getFont().getSize2D() + 1f));
        l.setBorder(BorderFactory.createEmptyBorder(12, 0, 2, 0));
        return l;
    }

    static JTextArea note(String text) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setOpaque(false);
        return a;
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
