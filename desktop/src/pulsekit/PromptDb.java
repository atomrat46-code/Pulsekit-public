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
 * files galleries (previews; a click plays a sound or a MIDI with the kit, or previews the file; a
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
        this.show(dialog, this.withPreviews(kinds, list, dialog, () -> this.browse(results, only, picked)), grid, cancel);
    }

    /**
     * The top of a gallery, with Make video previews (n) under it when some videos in `list` have
     * no preview yet: one browser tab reads each one's first frame, and the gallery opens again
     * with them.
     */
    JPanel withPreviews(JPanel top, List<PromptVault.StoredFile> list, JDialog dialog, Runnable reopen) {
        List<byte[]> missing = this.missingPreviews(list);
        if (missing.isEmpty()) return top;
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.add(top, BorderLayout.NORTH);
        JButton make = new JButton("Make video previews (" + missing.size() + ")");
        make.setName("make-video-previews");
        make.setToolTipText("Your browser reads each video's first frame; Pulsekit keeps it encrypted, so this is done once.");
        make.addActionListener(e -> this.makePreviews(missing, () -> {
            dialog.dispose();
            reopen.run();
        }));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(make);
        box.add(row, BorderLayout.SOUTH);
        return box;
    }

    /** The videos in `list` without a preview yet, each once. */
    List<byte[]> missingPreviews(List<PromptVault.StoredFile> list) {
        List<byte[]> out = new ArrayList<byte[]>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        try {
            PromptVault vault = vault();
            for (PromptVault.StoredFile f : list) {
                if (previewKind(f.name, null) != 3) continue;
                byte[] bytes = vault.fileBytes(f.versionId, f.which);
                if (bytes == null || bytes.length == 0 || ThumbCache.has(bytes) || !seen.add(ThumbCache.key(bytes))) continue;
                out.add(bytes);
            }
        } catch (Exception ex) {
            // no library: nothing to make
        }
        return out;
    }

    /** The previews grab under way, if any. */
    FrameGrab previews;

    /** Opens one browser tab that reads each video's first frame; they are kept, then `after` runs. */
    void makePreviews(List<byte[]> videos, Runnable after) {
        if (this.previews != null) this.previews.cancel();
        try {
            File dir = new File(new File(System.getProperty("java.io.tmpdir", "."), "pulsekit-library"), "previews");
            dir.mkdirs();
            final List<File> files = new ArrayList<File>();
            for (int i = 0; i < videos.size(); i++) {
                byte[] v = videos.get(i);
                // WebM and Matroska start with the EBML mark; MP4 and MOV are served as MP4.
                boolean webm = v.length >= 4 && (v[0] & 0xff) == 0x1a && (v[1] & 0xff) == 0x45 && (v[2] & 0xff) == 0xdf && (v[3] & 0xff) == 0xa3;
                File f = new File(dir, "video-" + i + (webm ? ".webm" : ".mp4"));
                Files.write(f.toPath(), videos.get(i));
                f.deleteOnExit();
                files.add(f);
            }
            final FrameGrab[] mine = new FrameGrab[1];
            mine[0] = new FrameGrab(files, made -> {
                if (this.previews == mine[0]) this.previews = null;
                int kept = 0;
                for (java.util.Map.Entry<Integer, byte[]> e : made.entrySet()) {
                    try {
                        ThumbCache.put(videos.get(e.getKey()), e.getValue());
                        kept++;
                    } catch (Exception ex) {
                        // that one stays without a preview
                    }
                }
                for (File f : files) f.delete();
                app.setNow("Made " + kept + " of " + videos.size() + (videos.size() == 1 ? " video preview" : " video previews"));
                if (kept > 0) after.run();
            });
            java.net.URI page = mine[0].start();
            this.previews = mine[0];
            browser.accept(page);
            app.setNow("Making video previews in the browser\u2026");
        } catch (Exception ex) {
            app.setNow("Could not make video previews: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
        }
    }

    /**
     * The Prompts page's DB button on a file row: the stored reference files (or result files, for
     * the Result file row) as cards; a click puts that file in the row. A right click opens, saves,
     * renames or deletes it.
     */
    void pickStored(String slot, boolean results, CardAction picked) {
        final JDialog dialog = new JDialog(app, "Database \u00b7 " + slot, true);
        JLabel head = new JLabel(results ? "Result files" : "Reference files");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.add(head);
        final JPanel[] grid = new JPanel[1];
        grid[0] = this.grid(stored(results), (file, card) -> {
            dialog.dispose();
            picked.run(file, card);
        }, (file, card) -> this.menu(file, card, () -> {
            dialog.dispose();
            this.pickStored(slot, results, picked);
        }), "db-pick:");
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        this.lastPick = dialog;
        this.show(dialog, this.withPreviews(top, stored(results), dialog, () -> this.pickStored(slot, results, picked)), grid[0], cancel);
    }

    /** The DB dialog shown last, for the tests. */
    JDialog lastPick;

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
        if (bytes == null) return null;
        // A video shows the preview the browser made of its first frame (Make video previews), when there is one.
        if (previewKind(name, bytes) == 3) {
            byte[] made = ThumbCache.get(bytes);
            return made == null ? null : thumb("preview.jpg", made);
        }
        if (!name.toLowerCase().matches(".+\\.(png|jpe?g|gif|bmp)")) return null;
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
        this.show(dialog, this.withPreviews(kinds, stored(results), dialog, () -> this.gallery(results)), grid, close);
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
            // A picture or text shows in Preview (with zoom), a video in the player; the rest opens.
            this.preview(file.name, bytes);
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

    // ------------------------------------------------------------ PyJav: a prompt's files

    /** A prompt's reference files written out of the library for PyJav, as on the phone. */
    static final class Refs {
        String ref1Path = "";
        String ref2Path = "";
        String description = "";
        String resultName = "";
        String note = "";
    }

    /**
     * Opening a .prompt in PyJav: the final version of the prompt it names (or the version with its
     * reference file names) gives its reference files, written to ~/.pulsekit/pyjav-in, its
     * description, and its result file's name.
     */
    static Refs refsFor(String text, String fallbackTitle) {
        Refs refs = new Refs();
        try {
            PromptRun.Sheet sheet = PromptRun.parse(text == null ? "" : text);
            String title = sheet != null && sheet.name != null ? sheet.name : "";
            if (title.length() == 0 && fallbackTitle != null) title = fallbackTitle.replaceAll("(?i)\\.prompt$", "");
            String category = sheet != null && sheet.category != null ? sheet.category : "";
            String n1 = sheet != null && sheet.ref1 != null ? sheet.ref1 : "";
            String n2 = sheet != null && sheet.ref2 != null ? sheet.ref2 : "";
            PromptVault.Version version = vault().selectedRefs(title, category, n1, n2);
            if (version == null) {
                refs.note = "Reference file 1: none\nReference file 2: none";
                return refs;
            }
            File dir = new File(dir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            String name1 = version.ref1Name == null ? "" : version.ref1Name;
            String name2 = version.ref2Name == null ? "" : version.ref2Name;
            refs.description = version.description == null ? "" : version.description;
            refs.resultName = version.result != null && version.result.length > 0 && version.resultName != null ? version.resultName : "";
            if (version.ref1 != null && version.ref1.length > 0) refs.ref1Path = write(dir, "ref1-", name1, version.ref1);
            if (version.ref2 != null && version.ref2.length > 0) refs.ref2Path = write(dir, "ref2-", name2, version.ref2);
            refs.note = line("Reference file 1", name1, refs.ref1Path) + "\n" + line("Reference file 2", name2, refs.ref2Path);
        } catch (Exception ex) {
            refs.note = "Could not read the encrypted database.";
        }
        return refs;
    }

    private static String line(String label, String name, String path) {
        if (path == null || path.length() == 0) return label + ": none";
        return label + ": " + (name == null || name.length() == 0 ? "file" : name) + "\n" + path;
    }

    private static String write(File dir, String prefix, String name, byte[] bytes) throws Exception {
        String raw = name == null || name.trim().length() == 0 ? "file" : name.trim();
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < raw.length() && sb.length() < 80 + prefix.length(); i++) {
            char c = raw.charAt(i);
            sb.append(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '-' || c == '_' ? c : '_');
        }
        File file = new File(dir, sb.toString());
        Files.write(file.toPath(), bytes);
        return file.getAbsolutePath();
    }

    /** A prompt run's output as the prompt's result file, when its version has none yet. Returns the name kept. */
    static String storeResult(String text, String fileName, byte[] bytes) {
        if (fileName == null || fileName.length() == 0 || bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) return "";
        try {
            PromptRun.Sheet sheet = PromptRun.parse(text == null ? "" : text);
            if (sheet == null) return "";
            PromptVault vault = vault();
            PromptVault.Version version = vault.selectedRefs(sheet.name, sheet.category, sheet.ref1, sheet.ref2);
            if (version == null) return "";
            if (version.result != null && version.result.length > 0 && version.resultName != null && version.resultName.length() > 0) return version.resultName;
            vault.putFile(version.id, 3, fileName, bytes);
            return fileName;
        } catch (Exception ex) {
            return "";
        }
    }

    // ------------------------------------------------------------ Preview

    /** What a file is, for Preview: 1 text, 2 picture, 3 video, 4 sound (MIDI too), 0 none. As on the phone. */
    static int previewKind(String name, byte[] bytes) {
        String low = name == null ? "" : name.toLowerCase();
        if (low.matches(".+\\.(txt|md|json|prompt|csv|xml|html|py|java|log|css|js)")) return 1;
        if (low.matches(".+\\.(png|jpe?g|webp|gif|bmp)")) return 2;
        if (low.matches(".+\\.(mp4|webm|mkv|3gp|mov)")) return 3;
        if (low.matches(".+\\.(wav|mp3|ogg|m4a|aac|flac|mid|midi)")) return 4;
        if (bytes == null) return 0;
        if (bytes.length >= 4 && bytes[0] == 'M' && bytes[1] == 'T' && bytes[2] == 'h' && bytes[3] == 'd') return 4;
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return 2;
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return 2;
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return 2;
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'A') return 4;
        if (bytes.length >= 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') return 4;
        if (bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') return 3;
        int n = Math.min(bytes.length, 4096);
        for (int i = 0; i < n; i++) {
            int b = bytes[i] & 0xff;
            if (b == 0 || b < 9) return 0;
        }
        return n > 0 ? 1 : 0;
    }

    /**
     * Preview, as on the phone: text in a window with zoom, a picture with zoom, a sound or a MIDI
     * (with the kit's sounds) with Play / Stop, a video in the browser player page or the system
     * player. Anything else opens in the computer's app for it.
     */
    void preview(String name, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            app.setNow("No file selected");
            return;
        }
        String shown = name == null || name.isEmpty() ? "file" : name;
        int kind = previewKind(shown, bytes);
        if (kind == 1 || kind == 2) {
            this.zoomPreview(shown, bytes, kind);
        } else if (kind == 3) {
            this.videoPreview(shown, bytes);
        } else if (kind == 4) {
            this.soundPreview(shown, bytes);
        } else {
            PromptVault.StoredFile f = new PromptVault.StoredFile();
            f.name = shown;
            this.open(f, bytes);
        }
    }

    /** Opens a page in the browser; the tests read the address instead. */
    static java.util.function.Consumer<java.net.URI> browser = uri -> {
        try {
            java.awt.Desktop.getDesktop().browse(uri);
        } catch (Exception ex) {
            throw new IllegalStateException("No browser opens " + uri, ex);
        }
    };

    /** The frame reading under way, if any (one at a time). */
    FrameGrab grab;

    /**
     * A video: Play (the browser player page, with Play/Pause, Stop, Mute and volume), Open (the
     * system player), Extract frames (its first and last frame as reference files, read by the
     * browser), or Close.
     */
    void videoPreview(String name, byte[] bytes) {
        File tmp = this.spill(name, bytes);
        if (tmp == null) return;
        Object[] options = new Object[] {"Play", "Open", "Extract frames", "Close"};
        int ans = JOptionPane.showOptionDialog(app, name + " (" + Math.max(1, bytes.length / 1024) + " KB)\n\n"
            + "Play opens it in the browser with Play/Pause, Stop, Mute and volume.\n"
            + "Extract frames keeps its first and last frame as reference files (the browser reads them).", "Preview \u00b7 " + name,
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        try {
            if (ans == 0) browser.accept(app.pyJav.videoPage(tmp).toURI());
            else if (ans == 1) java.awt.Desktop.getDesktop().open(tmp);
            else if (ans == 2) this.extractFrames(name, tmp);
        } catch (Exception ex) {
            app.setNow("Could not open a player for " + name);
        }
    }

    /** Extract frames: the browser reads the first and last frame; they go into the library as reference files 1 and 2. */
    void extractFrames(String name, File video) {
        if (this.grab != null) this.grab.cancel();
        final String stem = frameStem(name);
        final FrameGrab[] mine = new FrameGrab[1];
        mine[0] = new FrameGrab(video, name, (first, last, error) -> {
            if (this.grab == mine[0]) this.grab = null;
            if (error != null) {
                app.setNow(error);
                return;
            }
            try {
                PromptVault vault = vault();
                int saved = 0;
                if (first != null) {
                    vault.addReferenceImage(stem + "-first.jpg", first, stem, 1);
                    saved++;
                }
                if (last != null) {
                    vault.addReferenceImage(stem + "-last.jpg", last, stem, 2);
                    saved++;
                }
                app.setNow(saved == 2 ? "Saved first and last frame as reference files." : "Saved a frame as a reference file.");
            } catch (Exception ex) {
                app.setNow(ex.getMessage() != null ? ex.getMessage() : "Could not keep the frames");
            }
        });
        try {
            java.net.URI page = mine[0].start();
            this.grab = mine[0];
            browser.accept(page);
            app.setNow("Reading frames in the browser\u2026");
        } catch (Exception ex) {
            mine[0].cancel();
            app.setNow("Could not read frames: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
        }
    }

    /** The frames' names: the video's, without its extension, as on the phone. */
    static String frameStem(String name) {
        String clean = PromptVault.fileTitle(name);
        int dot = clean.lastIndexOf('.');
        if (dot > 0) clean = clean.substring(0, dot);
        clean = clean.replaceAll("[^A-Za-z0-9._-]", "_");
        if (clean.length() == 0) return "video";
        return clean.length() > 40 ? clean.substring(0, 40) : clean;
    }

    /** The file in the temp folder (for a player); null after saying why. */
    File spill(String name, byte[] bytes) {
        try {
            File tmp = new File(new File(System.getProperty("java.io.tmpdir", "."), "pulsekit-library"), name.replace('/', '_').replace('\\', '_'));
            tmp.getParentFile().mkdirs();
            Files.write(tmp.toPath(), bytes);
            tmp.deleteOnExit();
            return tmp;
        } catch (Exception ex) {
            app.setNow("Could not preview " + name);
            return null;
        }
    }

    /** The dialog shown last by Preview, for the tests. */
    JDialog lastPreview;

    private void zoomPreview(String name, byte[] bytes, int kind) {
        final JDialog dialog = new JDialog(app, "Preview \u00b7 " + name, true);
        final float[] zoom = new float[] {1f};
        final javax.swing.JComponent view;
        final Runnable apply;
        if (kind == 1) {
            javax.swing.JTextArea text = new javax.swing.JTextArea(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), 24, 70);
            text.setName("preview-text");
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            final java.awt.Font base = new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13);
            text.setFont(base);
            view = text;
            apply = () -> text.setFont(base.deriveFont(13f * zoom[0]));
        } else {
            java.awt.image.BufferedImage img;
            try {
                img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            } catch (Exception ex) {
                img = null;
            }
            if (img == null) {
                // WebP and others Java cannot read: the system viewer shows them.
                PromptVault.StoredFile f = new PromptVault.StoredFile();
                f.name = name;
                this.open(f, bytes);
                return;
            }
            final java.awt.image.BufferedImage picture = img;
            final double fit = Math.min(1.0, Math.min(760.0 / img.getWidth(), 520.0 / img.getHeight()));
            JLabel label = new JLabel();
            label.setName("preview-image");
            view = label;
            apply = () -> {
                int w = Math.max(1, (int) (picture.getWidth() * fit * zoom[0]));
                int h = Math.max(1, (int) (picture.getHeight() * fit * zoom[0]));
                label.setIcon(new ImageIcon(picture.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
            };
        }
        apply.run();
        JLabel percent = new JLabel("100%");
        JButton out = new JButton("\u2212");
        JButton in = new JButton("+");
        JButton reset = new JButton("100%");
        java.util.function.Consumer<Float> set = next -> {
            zoom[0] = Math.max(0.25f, Math.min(4f, next));
            percent.setText(Math.round(zoom[0] * 100) + "%");
            apply.run();
            view.revalidate();
        };
        out.addActionListener(e -> set.accept(zoom[0] / 1.25f));
        in.addActionListener(e -> set.accept(zoom[0] * 1.25f));
        reset.addActionListener(e -> set.accept(1f));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(new JLabel("Zoom"));
        top.add(out);
        top.add(percent);
        top.add(in);
        top.add(reset);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        body.add(top, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(view);
        scroll.setPreferredSize(new Dimension(800, 560));
        body.add(scroll, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottom.add(close);
        body.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(body);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        this.lastPreview = dialog;
        dialog.setVisible(true);
    }

    /** A sound (WAV, MP3) or a MIDI with the kit's sounds: Play / Stop and Close. Other sound files open in the system player. */
    private void soundPreview(String name, byte[] bytes) {
        short[] pcm = null;
        int sr = 22050;
        boolean midi = bytes.length >= 4 && bytes[0] == 'M' && bytes[1] == 'T' && bytes[2] == 'h' && bytes[3] == 'd';
        String low = name.toLowerCase();
        try {
            if (midi) {
                pcm = app.playback == null ? null : AudioIo.renderMidiDrums(bytes, app.playback.mixVoices(), sr);
            } else if (low.endsWith(".mp3") || (bytes.length >= 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3')) {
                AudioIo.Pcm d = Mp3Decode.parse(bytes);
                if (d != null) {
                    pcm = AudioIo.floatsToShorts(d.samples);
                    sr = d.sr;
                }
            } else if (bytes.length >= 12 && bytes[0] == 'R' && bytes[8] == 'W') {
                AudioIo.Pcm d = AudioIo.parseWav(bytes);
                if (d != null) {
                    pcm = AudioIo.floatsToShorts(d.samples);
                    sr = d.sr;
                }
            }
        } catch (Exception ex) {
            pcm = null;
        }
        if (pcm == null || pcm.length == 0) {
            if (midi) {
                app.setNow(name + " has no drums for the kit to play");
                return;
            }
            PromptVault.StoredFile f = new PromptVault.StoredFile();
            f.name = name;
            this.open(f, bytes);
            return;
        }
        byte[] raw = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            raw[2 * i] = (byte) pcm[i];
            raw[2 * i + 1] = (byte) (pcm[i] >> 8);
        }
        int seconds = Math.round(pcm.length / (float) sr);
        String length = (seconds / 60) + ":" + String.format(java.util.Locale.US, "%02d", seconds % 60);
        String note = midi ? "MIDI drums, played with Pulsekit's kit sounds (your imported SoundFont, if any)." : "Sound";
        javax.sound.sampled.Clip c = null;
        try {
            while (true) {
                boolean playing = c != null && c.isRunning();
                Object[] options = new Object[] {playing ? "Stop" : "Play", "Close"};
                int ans = JOptionPane.showOptionDialog(app, name + " (" + length + ")\n\n" + note, "Preview",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
                if (ans != 0) break;
                if (playing) {
                    c.stop();
                    continue;
                }
                try {
                    if (c == null) {
                        c = javax.sound.sampled.AudioSystem.getClip();
                        c.open(new javax.sound.sampled.AudioFormat(sr, 16, 1, true, false), raw, 0, raw.length);
                    }
                    c.setFramePosition(0);
                    c.start();
                } catch (Exception ex) {
                    app.setNow("No audio output for " + name);
                    break;
                }
            }
        } finally {
            if (c != null) {
                c.stop();
                c.close();
            }
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
