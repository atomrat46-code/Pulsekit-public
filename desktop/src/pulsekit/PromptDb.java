package pulsekit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * The prompt library on the desktop, as on the phone: the same encrypted file (prompts.vault in
 * ~/.pulsekit), its key in ~/.pulsekit/prompts.key (owner only). Here: the Ref files and Result
 * files galleries (previews; a click plays a sound or a MIDI with the kit, or opens the file; a
 * right click opens, saves, renames or deletes it), Browse DB (a stored file as a program's input,
 * a MIDI played with the kit to a WAV where a sound is wanted), and File > Import as Ref file /
 * Import as Result file.
 */
final class PromptDb {
    /** Only sound files (an audio input: DrumMidi, CompareHits, SplitWav, CutWav). */
    static final String SOUNDS = "sounds";
    /** Only MIDI files (CompareHits' drums and song). */
    static final String MIDIS = "midis";
    /** Sound files and MIDI files (DrumMidi's input: a MIDI is played with the kit to a WAV). */
    static final String SOUNDS_OR_MIDIS = "sounds-or-midis";
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private static final int CELL = 150;

    final Pulsekit app;
    /** The sound playing in a gallery: its clip, and the card that plays it. */
    javax.sound.sampled.Clip clip;
    JButton playingCard;
    /** The gallery shown last, for the tests. */
    JDialog lastGallery;

    PromptDb(Pulsekit app) {
        this.app = app;
    }

    /** Where the library is kept (~/.pulsekit); sets the desktop key the first time. */
    static synchronized File dir() {
        File d = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (PromptVault.keys == null) PromptVault.keys = new DesktopVaultKey(d);
        return d;
    }

    static PromptVault vault() throws Exception {
        return PromptVault.open(dir());
    }

    static boolean fits(String name, String only) {
        if (only == null) return true;
        String low = name == null ? "" : name.toLowerCase();
        if (SOUNDS.equals(only)) return low.matches(".+\\.(wav|wave|mp3|flac|m4a|ogg|aac)");
        if (MIDIS.equals(only)) return low.matches(".+\\.(mid|midi)");
        if (SOUNDS_OR_MIDIS.equals(only)) return fits(name, SOUNDS) || fits(name, MIDIS);
        return true;
    }

    /** The library's reference (or result) files of the `only` kind, each name and size once; empty when it cannot be read. */
    static List<PromptVault.StoredFile> files(boolean results, String only) {
        List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>();
        try {
            java.util.HashSet<String> seen = new java.util.HashSet<String>();
            PromptVault vault = vault();
            for (PromptVault.StoredFile f : results ? vault.resultFiles() : vault.referenceFiles()) {
                if (fits(f.name, only) && seen.add(f.name + "\n" + f.size)) out.add(f);
            }
        } catch (Exception ex) {
            // No library yet (or no key): nothing to list.
        }
        return out;
    }

    /** Every stored reference (or result) file, as the Prompts page's galleries list them (a copy in each prompt version). */
    static List<PromptVault.StoredFile> stored(boolean results) {
        try {
            PromptVault vault = vault();
            return new ArrayList<PromptVault.StoredFile>(results ? vault.resultFiles() : vault.referenceFiles());
        } catch (Exception ex) {
            return new ArrayList<PromptVault.StoredFile>();
        }
    }

    /** Reference and result files together: Browse DB is greyed when there are none. */
    static List<PromptVault.StoredFile> allFiles(String only) {
        List<PromptVault.StoredFile> out = files(false, only);
        out.addAll(files(true, only));
        return out;
    }

    /** What to do with the picked file, copied into PyJav's input folder (its name, and the copy). */
    interface Picked {
        void picked(String name, File file);
    }

    // ------------------------------------------------------------ Browse DB

    void browse(String only, Picked picked) {
        this.browse(files(false, only).isEmpty(), only, picked);
    }

    /** A grid of the library's files of the `only` kind; a click hands the picked one on. Ref files / Result files at the top. */
    void browse(boolean results, String only, Picked picked) {
        final JDialog dialog = new JDialog(app, results ? "Result files" : "Reference files", true);
        JPanel kinds = this.kinds(results, k -> files(k, only).size(), showResults -> {
            dialog.dispose();
            this.browse(showResults, only, picked);
        });
        List<PromptVault.StoredFile> list = files(results, only);
        JPanel grid = this.grid(list, (file, card) -> {
            File copy = this.copyOut(file);
            if (copy == null) {
                app.setNow("Could not read " + file.name + " from the library");
                return;
            }
            dialog.dispose();
            picked.picked(file.name, copy);
        }, null, "refs-pick:");
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        this.show(dialog, kinds, grid, cancel);
    }

    /** Ref files (n) / Result files (n): the one shown is greyed; the other opens in its place. */
    private JPanel kinds(boolean results, java.util.function.Function<Boolean, Integer> count, java.util.function.Consumer<Boolean> open) {
        JPanel kinds = new JPanel(new GridLayout(1, 2, 8, 0));
        for (int k = 0; k < 2; k++) {
            final boolean showResults = k == 1;
            JButton kind = new JButton((showResults ? "Result files" : "Ref files") + " (" + count.apply(showResults) + ")");
            kind.setName(showResults ? "refs-kind:results" : "refs-kind:refs");
            kind.setEnabled(showResults != results);
            kind.addActionListener(e -> open.accept(showResults));
            kinds.add(kind);
        }
        return kinds;
    }

    interface CardAction {
        void run(PromptVault.StoredFile file, JButton card);
    }

    /** The files as cards: a preview (a picture's own, else its type), the name, the prompt and size. */
    private JPanel grid(List<PromptVault.StoredFile> list, CardAction click, CardAction menu, String tag) {
        JPanel grid = new JPanel(new GridLayout(0, 3, 8, 8));
        if (list.isEmpty()) grid.add(new JLabel("none"));
        PromptVault vault;
        try {
            vault = vault();
        } catch (Exception ex) {
            vault = null;
        }
        for (final PromptVault.StoredFile file : list) {
            String name = file.name == null ? "file" : file.name;
            JButton card = new JButton();
            card.setName(tag + name);
            card.setVerticalTextPosition(SwingConstants.BOTTOM);
            card.setHorizontalTextPosition(SwingConstants.CENTER);
            card.setPreferredSize(new Dimension(CELL + 20, CELL + 60));
            ImageIcon thumb = vault == null ? null : thumb(name, vault.fileBytes(file.versionId, file.which));
            if (thumb != null) card.setIcon(thumb);
            card.setText(this.caption(file, thumb == null ? mark(name) : null));
            card.addActionListener(e -> click.run(file, card));
            if (menu != null) {
                card.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        if (e.isPopupTrigger()) menu.run(file, card);
                    }

                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        if (e.isPopupTrigger()) menu.run(file, card);
                    }
                });
            }
            grid.add(card);
        }
        return grid;
    }

    private String caption(PromptVault.StoredFile file, String mark) {
        String safe = (file.name == null ? "file" : file.name).replace("&", "&amp;").replace("<", "&lt;");
        String sub = (file.promptTitle == null ? "" : file.promptTitle.replace("&", "&amp;").replace("<", "&lt;")) + " · " + (file.size / 1024) + " KB";
        return "<html><center>" + (mark == null ? "" : "<div style='font-size:18px;color:#8A8B86;padding:30px'>" + mark + "</div>")
            + "<div style='width:" + CELL + "px'>" + safe + "</div><div style='font-size:9px;color:#8A8B86'>" + sub + "</div></center></html>";
    }

    /** The file's type in capitals (WAV, MID, MP4), shown where there is no preview. */
    static String mark(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toUpperCase() : "FILE";
    }

    /** A picture's preview, scaled into the cell; null for anything Java cannot read as a picture. */
    static ImageIcon thumb(String name, byte[] bytes) {
        if (bytes == null || !name.toLowerCase().matches(".+\\.(png|jpe?g|gif|bmp)")) return null;
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img == null) return null;
            double s = Math.min(CELL / (double) img.getWidth(), CELL / (double) img.getHeight());
            int w = Math.max(1, (int) (img.getWidth() * s));
            int h = Math.max(1, (int) (img.getHeight() * s));
            return new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH));
        } catch (Exception ex) {
            return null;
        }
    }

    private void show(JDialog dialog, JPanel top, JPanel grid, JButton close) {
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        body.add(top, BorderLayout.NORTH);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(grid, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrap);
        scroll.setPreferredSize(new Dimension(3 * (CELL + 28) + 24, 460));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        body.add(scroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottom.add(close);
        body.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(body);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
    }

    /** Copies the stored file into PyJav's input folder; null when it cannot. */
    File copyOut(PromptVault.StoredFile file) {
        try {
            byte[] bytes = vault().fileBytes(file.versionId, file.which);
            if (bytes == null || bytes.length == 0) return null;
            File in = new File(dir(), "pyjav-in");
            if (!in.isDirectory()) in.mkdirs();
            File out = new File(in, (file.name == null ? "reference" : file.name).replace('/', '_').replace('\\', '_').replace(' ', '_'));
            Files.write(out.toPath(), bytes);
            return out;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * A MIDI file's drums played with the kit's current sounds (the imported SoundFont, the active
     * drum set), written as <name>-kit.wav beside it; null after saying why when there are none.
     */
    File kitWav(String name, File file) {
        try {
            short[] pcm = app.playback == null ? null : AudioIo.renderMidiDrums(Files.readAllBytes(file.toPath()), app.playback.mixVoices(), 22050);
            if (pcm == null || pcm.length == 0) {
                app.setNow(name + " has no drums for the kit to play: pick a WAV, or a MIDI with drums");
                return null;
            }
            String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
            File wav = new File(file.getParentFile(), stem.replace(' ', '_') + "-kit.wav");
            Files.write(wav.toPath(), AudioIo.encodeWav(pcm, 22050));
            return wav;
        } catch (Exception ex) {
            app.setNow("Could not play " + name + " with the kit");
            return null;
        }
    }

    /** An audio row a MIDI can stand in for: a sound fills it as it is, a MIDI as its kit WAV. */
    void useAsAudio(String name, File file, String[] values, int index, JLabel label) {
        if (!fits(name, MIDIS)) {
            values[index] = file.getAbsolutePath();
            label.setText(name + " · from DB");
            return;
        }
        File wav = this.kitWav(name, file);
        if (wav == null) return;
        values[index] = wav.getAbsolutePath();
        label.setText(name + " → " + wav.getName() + " (kit sounds)");
    }

    // ------------------------------------------------------------ Ref files / Result files

    /** The Prompts page's Ref files / Result files: previews; a click plays or opens, a right click has the rest. */
    void gallery(boolean results) {
        final JDialog dialog = new JDialog(app, results ? "Result files" : "Ref files", true);
        JPanel kinds = this.kinds(results, k -> stored(k).size(), showResults -> {
            dialog.dispose();
            this.gallery(showResults);
        });
        JPanel grid = this.grid(stored(results), (file, card) -> this.preview(file, card),
            (file, card) -> this.menu(file, card, () -> {
                dialog.dispose();
                this.gallery(results);
            }), "gallery:");
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                PromptDb.this.stop();
            }
        });
        this.lastGallery = dialog;
        this.show(dialog, kinds, grid, close);
    }

    /** A card's click: a sound (WAV, MP3) or a MIDI (with the kit) plays in place, again stops; anything else opens. */
    void preview(PromptVault.StoredFile file, JButton card) {
        if (card == this.playingCard) {
            this.stop();
            return;
        }
        this.stop();
        byte[] bytes;
        try {
            bytes = vault().fileBytes(file.versionId, file.which);
        } catch (Exception ex) {
            bytes = null;
        }
        if (bytes == null) {
            app.setNow("Could not read " + file.name);
            return;
        }
        short[] pcm = null;
        int sr = 22050;
        String low = file.name == null ? "" : file.name.toLowerCase();
        try {
            if (fits(low, MIDIS)) {
                pcm = app.playback == null ? null : AudioIo.renderMidiDrums(bytes, app.playback.mixVoices(), sr);
                if (pcm == null || pcm.length == 0) {
                    app.setNow(file.name + " has no drums for the kit to play");
                    return;
                }
            } else if (low.matches(".+\\.(wav|wave|mp3)")) {
                AudioIo.Pcm decoded = low.endsWith(".mp3") ? Mp3Decode.parse(bytes) : AudioIo.parseWav(bytes);
                if (decoded != null) {
                    pcm = AudioIo.floatsToShorts(decoded.samples);
                    sr = decoded.sr;
                }
            }
        } catch (Exception ex) {
            pcm = null;
        }
        if (pcm == null) {
            this.open(file, bytes);
            return;
        }
        byte[] raw = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            raw[2 * i] = (byte) pcm[i];
            raw[2 * i + 1] = (byte) (pcm[i] >> 8);
        }
        try {
            final javax.sound.sampled.Clip c = javax.sound.sampled.AudioSystem.getClip();
            c.open(new javax.sound.sampled.AudioFormat(sr, 16, 1, true, false), raw, 0, raw.length);
            c.addLineListener(ev -> {
                if (ev.getType() == javax.sound.sampled.LineEvent.Type.STOP) SwingUtilities.invokeLater(() -> {
                    if (this.clip == c) this.stop();
                });
            });
            this.clip = c;
            this.playingCard = card;
            card.setBorder(BorderFactory.createLineBorder(new Color(0xE0B04A), 2));
            card.setToolTipText("Playing: click to stop");
            c.start();
            app.setNow("Playing " + file.name + (fits(low, MIDIS) ? " with the kit" : ""));
        } catch (Exception ex) {
            this.stop();
            app.setNow("No audio output for " + file.name);
        }
    }

    boolean playing() {
        return this.clip != null;
    }

    void stop() {
        javax.sound.sampled.Clip c = this.clip;
        this.clip = null;
        if (c != null) {
            try {
                c.stop();
                c.close();
            } catch (Exception ignored) {
                // closed already
            }
        }
        if (this.playingCard != null) {
            this.playingCard.setBorder(new JButton().getBorder());
            this.playingCard.setToolTipText(null);
        }
        this.playingCard = null;
    }

    /** Opens the file with the system's app for it (a copy in the temp folder). */
    void open(PromptVault.StoredFile file, byte[] bytes) {
        try {
            File tmp = new File(new File(System.getProperty("java.io.tmpdir", "."), "pulsekit-library"), (file.name == null ? "file" : file.name).replace('/', '_').replace('\\', '_'));
            tmp.getParentFile().mkdirs();
            Files.write(tmp.toPath(), bytes);
            tmp.deleteOnExit();
            java.awt.Desktop.getDesktop().open(tmp);
            app.setNow("Opened " + file.name);
        } catch (Exception ex) {
            app.setNow("No app opens " + file.name);
        }
    }

    /** The right-click menu: Open, Save as, Rename, Delete. `changed` shows the gallery again. */
    void menu(PromptVault.StoredFile file, JButton card, Runnable changed) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open");
        open.addActionListener(e -> {
            try {
                this.open(file, vault().fileBytes(file.versionId, file.which));
            } catch (Exception ex) {
                app.setNow("Could not read " + file.name);
            }
        });
        JMenuItem save = new JMenuItem("Save as…");
        save.addActionListener(e -> this.saveAs(file));
        JMenuItem rename = new JMenuItem("Rename");
        rename.addActionListener(e -> {
            String now = JOptionPane.showInputDialog(app, "New name for " + file.name, file.name);
            if (now == null || now.trim().isEmpty() || now.trim().equals(file.name)) return;
            if (this.rename(file, now.trim())) changed.run();
        });
        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> {
            int ans = JOptionPane.showOptionDialog(app, "Delete " + file.name + " from the prompt library?", "Delete file",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, new Object[] {"Delete", "Cancel"}, "Cancel");
            if (ans != 0) return;
            if (this.delete(file)) changed.run();
        });
        menu.add(open);
        menu.add(save);
        menu.add(rename);
        menu.add(delete);
        menu.show(card, card.getWidth() / 2, card.getHeight() / 2);
    }

    void saveAs(PromptVault.StoredFile file) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(file.name == null ? "file" : file.name));
        if (chooser.showSaveDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
        try {
            Files.write(chooser.getSelectedFile().toPath(), vault().fileBytes(file.versionId, file.which));
            app.setNow("Saved " + chooser.getSelectedFile().getName());
        } catch (Exception ex) {
            app.setNow("Could not save " + file.name);
        }
    }

    boolean rename(PromptVault.StoredFile file, String name) {
        try {
            app.setNow("Renamed to " + vault().renameFile(file.versionId, file.which, name));
            return true;
        } catch (Exception ex) {
            app.setNow(ex.getMessage() != null ? ex.getMessage() : "Could not rename " + file.name);
            return false;
        }
    }

    boolean delete(PromptVault.StoredFile file) {
        try {
            vault().deleteFile(file.versionId, file.which);
            app.setNow("Deleted " + file.name);
            return true;
        } catch (Exception ex) {
            app.setNow("Could not delete " + file.name);
            return false;
        }
    }

    // ------------------------------------------------------------ File > Import as Ref file / Result file

    void importFile(boolean result) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(result ? "Import as Result file" : "Import as Ref file");
        if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
        this.importPicked(chooser.getSelectedFile(), result);
    }

    /** Asks to add the file (OK / Cancel), then stores it on its own, not with a prompt. */
    void importPicked(File file, boolean result) {
        String name = file.getName();
        if (file.length() > MAX_BYTES) {
            app.setNow(name + " is over the library's 16 MB");
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (Exception ex) {
            app.setNow("Could not read " + name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            return;
        }
        String kind = result ? "result file" : "reference file";
        int ans = JOptionPane.showOptionDialog(app, "Add " + name + " (" + Math.max(1, bytes.length / 1024) + " KB) to the prompt library as a " + kind + "?\n\n"
            + (result ? "It shows in Result files on the Prompts page." : "It shows in Ref files on the Prompts page and in Browse DB."),
            result ? "Import as Result file" : "Import as Ref file", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            new Object[] {"OK", "Cancel"}, "OK");
        if (ans != 0) return;
        try {
            vault().addLibraryFile(name, bytes, "Imported", result ? 3 : 1);
            app.setNow("Imported " + name + " as a " + kind);
        } catch (Exception ex) {
            app.setNow("Could not import " + name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
    }
}
