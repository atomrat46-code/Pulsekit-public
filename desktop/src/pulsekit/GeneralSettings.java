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
 * File > General settings: "Use encrypted DB" and "Ask where to save a prompt after a run" (PromptSave). Unticked, the prompt library is copied into an
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
        middle.add(note, BorderLayout.NORTH);
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
        for (java.awt.Component c : more.getComponents()) ((javax.swing.JComponent) c).setAlignmentX(0f);
        JPanel all = new JPanel(new BorderLayout(0, 8));
        all.add(middle, BorderLayout.NORTH);
        all.add(more, BorderLayout.CENTER);
        col.add(all, BorderLayout.CENTER);
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
