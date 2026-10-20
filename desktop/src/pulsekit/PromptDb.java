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
    /** MP4 videos (SogniVideo's Join with this video). */
    static final String VIDEOS = "videos";
    /** Text files (a prompt field's Browse DB: an Answer Prompt 1.txt). */
    static final String TEXTS = "texts";
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private static final int CELL = 150;

    final Pulsekit app;
    /** The sound playing in a gallery: its clip, and the card that plays it. */
    javax.sound.sampled.Clip clip;
    JButton playingCard;
    /** Where a gallery's or Browse DB's sound plays: the system's audio output (the tests give one of their own). */
    static java.util.concurrent.Callable<javax.sound.sampled.Clip> clips = javax.sound.sampled.AudioSystem::getClip;
    /** The gallery shown last, for the tests. */
    JDialog lastGallery;

    PromptDb(Pulsekit app) {
        this.app = app;
    }

    /** Where the library is kept (~/.pulsekit); sets the desktop key the first time. */
    static synchronized File dir() {
        File d = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (PromptVault.keys == null) PromptVault.keys = new DesktopVaultKey(d);
        // Runs a stopped app left in the pending list are cleared, once.
        PendingOps.start(d);
        return d;
    }

    static PromptVault vault() throws Exception {
        return PromptVault.open(dir());
    }

    static boolean fits(String name, String only) {
        if (only == null) return true;
        String low = name == null ? "" : name.toLowerCase();
        if (SOUNDS.equals(only)) return low.matches(".+\\.(wav|wave|mp3|flac|m4a|ogg|aac)");
        if (MIDIS.equals(only)) return low.matches(".+\\.(mid|midi|h2song|h2pattern|rpp)");
        if (SOUNDS_OR_MIDIS.equals(only)) return fits(name, SOUNDS) || fits(name, MIDIS);
        if (VIDEOS.equals(only)) return low.matches(".+\\.(mp4|m4v|mov)");
        if (TEXTS.equals(only)) return DbFilter.isText(name);
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

    /** The `only` kind's files, then only those Browse DB's A / I / V / S / T button picked last shows. */
    static List<PromptVault.StoredFile> shown(boolean results, String only) {
        List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>();
        // F: only the favourites; G: only the files of the group G shows.
        FileGroups.load(dir());
        FileFavourites.load(dir());
        // C and the tags read the files' prompts.
        MediaTags.load(dir());
        FileFilter.use(FileFilter.BROWSE);
        DbFilter.current = FileFilter.normal(DbFilter.current);
        PromptVault lib;
        try {
            lib = vault();
        } catch (Exception ex) {
            lib = null;
        }
        out.addAll(FileFilter.keep(files(results, only), lib, DbFilter.current));
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

    /** The prompt files (the sheets runs saved with --saveprompt), as the Prompts page's Prompt files lists them. */
    static List<PromptVault.StoredFile> promptFiles() {
        try {
            return new ArrayList<PromptVault.StoredFile>(vault().promptFiles());
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
        FileFilter.use(FileFilter.BROWSE);
        final JDialog dialog = new JDialog(app, (results ? "Result files" : "Reference files") + FileFilter.title(), true);
        JPanel kinds = this.kinds(results, k -> shown(k, only).size(), showResults -> {
            // Each tab keeps its place: the one left is noted, the one opened goes back to its own.
            browseScroll[results ? 1 : 0] = this.scrolledTo();
            this.restoreScroll = browseScroll[showResults ? 1 : 0];
            dialog.dispose();
            this.browse(showResults, only, picked);
        });
        // A / I / V / S / T: all files, images, videos, sound files, text files; the one shown is greyed.
        // G: a group's files (a right click on G picks the group).
        JPanel letters = new JPanel(new GridLayout(1, DbFilter.LETTERS.length + 3, 6, 0));
        final Runnable again = () -> {
            dialog.dispose();
            this.browse(results, only, picked);
        };
        for (String letter : withGroup()) {
            if (FileFilter.isToggle(letter)) continue;
            final JButton b = new JButton(letter);
            b.setName("db-filter:" + letter);
            b.setToolTipText(DbFilter.label(letter));
            b.setEnabled(!letter.equals(DbFilter.current));
            b.addActionListener(e -> {
                if (letter.equals(DbFilter.current)) return;
                DbFilter.current = letter;
                again.run();
            });
            letters.add(b);
        }
        // F G C: switches with the letter (a right click: G the groups, C the categories).
        FilterBar.toggles(app, letters, "db-filter:", DbFilter.current, again);
        // T1 T2 T3: the files with any of the tags picked.
        JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        FilterBar.tags(tagRow, "db-filter:", again);
        JPanel top = new JPanel(new GridLayout(3, 1, 0, 6));
        top.add(kinds);
        top.add(letters);
        top.add(tagRow);
        List<PromptVault.StoredFile> list = shown(results, only);
        JPanel grid = this.grid(list, (file, card) -> {
            File copy = this.copyOut(file);
            if (copy == null) {
                app.setNow("Could not read " + file.name + " from the library");
                return;
            }
            lastPicked = file.name;
            dialog.dispose();
            picked.picked(file.name, copy);
        }, (file, card) -> {
            // Right click: Add to favourites (★ on the card), Add to group.
            FileFavourites.load(dir());
            final boolean fav = FileFavourites.is(file);
            javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
            JMenuItem favItem = new JMenuItem(fav ? "Remove from favourites" : "Add to favourites");
            favItem.setName("refs-favourite");
            favItem.addActionListener(e -> {
                boolean now = FileFavourites.toggle(file);
                app.setNow(file.name + (now ? " added to favourites" : " taken out of favourites"));
                again.run();
            });
            m.add(favItem);
            m.add(GroupMenus.item(app, card, FileGroups.keys(file), file.name, FileFilter.active() ? again : null));
            JMenuItem tagsItem = new JMenuItem(MediaTags.TAGS);
            tagsItem.setName("refs-tags");
            tagsItem.addActionListener(e -> TagDialogs.tags(app, card, file.name, file.size, null));
            m.add(tagsItem);
            JMenuItem catItem = new JMenuItem(MediaTags.CATEGORY);
            catItem.setName("refs-category");
            catItem.addActionListener(e -> TagDialogs.category(app, card, file.name, file.size, null));
            m.add(catItem);
            // Add to playlist (a list picked, or a new one) and Add to default playlist.
            m.add(PlaylistWindow.addMenu(app, Playlist.dbAddress(file), file.name));
            m.add(PlaylistWindow.addDefaultItem(app, Playlist.dbAddress(file), file.name));
            this.lastCardMenu = m;
            MenuCancel.add(m);
            m.show(card, 10, 10);
        }, "refs-pick:", true);
        // The file picked last: outlined, and scrolled to (unless a tab switch brings back that tab's place).
        final boolean switched = this.restoreScroll >= 0;
        for (java.awt.Component c : grid.getComponents()) {
            if (lastPicked != null && c instanceof JButton && ("refs-pick:" + lastPicked).equals(c.getName())) {
                final JButton was = (JButton) c;
                was.setBorder(BorderFactory.createLineBorder(new java.awt.Color(0xE0B04A), 2));
                was.putClientProperty("refs-last", Boolean.TRUE);
                if (!switched) SwingUtilities.invokeLater(() -> was.scrollRectToVisible(new java.awt.Rectangle(0, 0, was.getWidth(), was.getHeight())));
                break;
            }
        }
        this.stopOnClose(dialog);
        this.lastBrowse = dialog;
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        this.show(dialog, this.withPreviews(top, list, dialog, () -> this.browse(results, only, picked)), grid, cancel);
    }

    /**
     * The top of a gallery, with Make video previews (n) under it when some videos in `list` have
     * no preview yet: one browser tab reads each one's first frame, and the gallery opens again
     * with them.
     */
    JPanel withPreviews(JPanel top, List<PromptVault.StoredFile> list, JDialog dialog, Runnable reopen) {
        List<byte[]> missing = this.missingPreviews(list);
        if (missing.isEmpty()) return top;
        // With VLC they are made at once, in the background, without a browser tab; the cards fill in.
        if (VlcPlayer.available()) {
            this.vlcPreviews(missing, dialog);
            return top;
        }
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

    /** The VLC previews under way (one run at a time), for the tests. */
    volatile Thread vlcPreviews;

    /**
     * Video previews made with VLC: each video's first frame, read without sound in the
     * background, kept encrypted like the browser's, and put on its cards in `dialog` as it comes.
     * Stops when the dialog closes.
     */
    void vlcPreviews(List<byte[]> videos, JDialog dialog) {
        final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                closed.set(true);
            }
        });
        app.setNow("Making video previews with VLC\u2026");
        Thread t = new Thread(() -> {
            int made = 0;
            File dir = new File(new File(System.getProperty("java.io.tmpdir", "."), "pulsekit-library"), "previews");
            dir.mkdirs();
            for (int i = 0; i < videos.size() && !closed.get(); i++) {
                byte[] v = videos.get(i);
                boolean webm = v.length >= 4 && (v[0] & 0xff) == 0x1a && (v[1] & 0xff) == 0x45 && (v[2] & 0xff) == 0xdf && (v[3] & 0xff) == 0xa3;
                File f = new File(dir, "vlc-" + i + (webm ? ".webm" : ".mp4"));
                try {
                    Files.write(f.toPath(), v);
                    byte[] jpeg = VlcPlayer.firstFrame(f, 320);
                    if (jpeg == null) continue;
                    ThumbCache.put(v, jpeg);
                    made++;
                    final String key = ThumbCache.key(v);
                    final ImageIcon icon = thumb("preview.jpg", jpeg);
                    SwingUtilities.invokeLater(() -> this.fillCards(dialog.getContentPane(), key, icon));
                } catch (Exception ex) {
                    // that one keeps its type label
                } finally {
                    f.delete();
                }
            }
            final int count = made;
            SwingUtilities.invokeLater(() -> app.setNow("Made " + count + " of " + videos.size() + (videos.size() == 1 ? " video preview" : " video previews") + " with VLC"));
            this.vlcPreviews = null;
        }, "pulsekit-vlc-previews");
        t.setDaemon(true);
        this.vlcPreviews = t;
        t.start();
    }

    /** Puts a video's new preview on every card that shows it. */
    private void fillCards(java.awt.Container root, String key, ImageIcon icon) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof JButton && key.equals(((JButton) c).getClientProperty("video-key"))) {
                JButton card = (JButton) c;
                card.setIcon(icon);
                lastSize = icon == null ? "" : MediaSize.aspect(icon.getIconWidth(), icon.getIconHeight());
                card.setText(this.caption((PromptVault.StoredFile) card.getClientProperty("stored"), null));
            }
            if (c instanceof java.awt.Container) this.fillCards((java.awt.Container) c, key, icon);
        }
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
        }), "db-pick:", true);
        this.stopOnClose(dialog);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        this.lastPick = dialog;
        this.show(dialog, this.withPreviews(top, stored(results), dialog, () -> this.pickStored(slot, results, picked)), grid[0], cancel);
    }

    /** The DB dialog shown last, for the tests. */
    JDialog lastPick;

    /** The ★ Favourite files list's thumbnails, by row (for the tests). */
    java.util.Map<Integer, javax.swing.Icon> favIcons;

    /** A Browse DB card's right click menu shown last, for the tests. */
    javax.swing.JPopupMenu lastCardMenu;

    /** A I V S T, F (favourites) and G (a group). */
    static String[] withGroup() {
        // Without the types General settings leaves out (A when the one shown is).
        DbFilter.current = AppSettings.letter(DbFilter.current);
        return AppSettings.letters(DbFilter.WITH_FAV_GROUP);
    }

    /** The Browse DB dialog shown last, for the tests. */
    JDialog lastBrowse;

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
        return this.grid(list, click, menu, tag, false);
    }

    /** As above; with `play`, a sound or MIDI card has ▶ Play under it (the card itself picks the file). */
    private JPanel grid(List<PromptVault.StoredFile> list, CardAction click, CardAction menu, String tag, boolean play) {
        JPanel grid = new JPanel(new GridLayout(0, 3, 8, 8));
        if (list.isEmpty()) grid.add(new JLabel("none"));
        PromptVault vault;
        try {
            vault = vault();
        } catch (Exception ex) {
            vault = null;
        }
        // The cards' previews: their reads are no "Processing DB" (one after another, the notice kept coming back).
        PromptVault.quiet(true);
        try {
            return this.cards(grid, vault, list, click, menu, tag, play);
        } finally {
            PromptVault.quiet(false);
        }
    }

    private JPanel cards(JPanel grid, PromptVault vault, List<PromptVault.StoredFile> list, CardAction click, CardAction menu, String tag, boolean play) {
        for (final PromptVault.StoredFile file : list) {
            String name = file.name == null ? "file" : file.name;
            JButton card = new JButton();
            card.setName(tag + name);
            card.setVerticalTextPosition(SwingConstants.BOTTOM);
            card.setHorizontalTextPosition(SwingConstants.CENTER);
            card.setPreferredSize(new Dimension(CELL + 20, CELL + 60));
            // A sound or text is not read for its card (a large WAV read for every card ran out of memory); a MIDI is, for its length.
            byte[] bytes = vault == null || MediaDir.noThumb(name) && !MediaDir.isMidi(name) ? null : vault.fileBytes(file.versionId, file.which);
            lastSize = "";
            MidiInfo midi = MediaDir.isMidi(name) ? MidiInfo.of(bytes) : null;
            lastMidi = midi == null ? "" : midi.line();
            ImageIcon thumb = bytes == null ? null : thumb(name, bytes);
            // A prompt file: small thumbnails of its prompt's reference and result files.
            if (thumb == null && vault != null && file.which == PromptVault.PROMPT_FILE) thumb = slotStrip(vault, vault.versionOf(file), CELL / 3);
            if (thumb != null) card.setIcon(thumb);
            card.putClientProperty("size", lastSize);
            // A video without a preview yet: its card is filled in when one is made (VLC).
            if (thumb == null && bytes != null && bytes.length > 0 && previewKind(name, bytes) == 3) {
                card.putClientProperty("video-key", ThumbCache.key(bytes));
                card.putClientProperty("stored", file);
            }
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
            if ("gallery:".equals(tag) && fits(name, SOUNDS_OR_MIDIS)) {
                // The gallery's sound or MIDI: ▶ ❚❚ ■ and, while it plays, mm:ss / mm:ss.
                JPanel cell = new JPanel(new BorderLayout(0, 2));
                cell.add(card, BorderLayout.CENTER);
                JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
                final JLabel time = new JLabel(" ");
                time.setName("gallery-time:" + name);
                JButton go = new JButton("\u25b6");
                go.setName("gallery-play:" + name);
                go.addActionListener(e -> this.resume(file, card, time));
                JButton hold = new JButton("\u275a\u275a");
                hold.setName("gallery-pause:" + name);
                hold.addActionListener(e -> {
                    if (this.playingCard == card) this.pause();
                });
                JButton halt = new JButton("\u25a0");
                halt.setName("gallery-stop:" + name);
                halt.addActionListener(e -> {
                    if (this.playingCard == card) this.stop();
                });
                controls.add(go);
                controls.add(hold);
                controls.add(halt);
                controls.add(time);
                cell.add(controls, BorderLayout.SOUTH);
                grid.add(cell);
            } else if (play && fits(name, SOUNDS_OR_MIDIS)) {
                JPanel cell = new JPanel(new BorderLayout(0, 2));
                cell.add(card, BorderLayout.CENTER);
                JButton listen = new JButton(PLAY);
                listen.setName("refs-play:" + name);
                listen.putClientProperty("play", Boolean.TRUE);
                listen.setToolTipText(fits(name, MIDIS) ? "Plays it with Pulsekit's kit sounds" : "Plays it here; the card picks it");
                listen.addActionListener(e -> this.preview(file, listen));
                cell.add(listen, BorderLayout.SOUTH);
                grid.add(cell);
            } else {
                grid.add(card);
            }
        }
        return grid;
    }

    static final String PLAY = "\u25b6 Play";
    static final String STOP = "\u25a0 Stop";

    /** Stops a sound playing in a Browse DB dialog when it closes (a pick, Cancel, the other list). */
    private void stopOnClose(JDialog dialog) {
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                PromptDb.this.stop();
            }
        });
    }

    private String caption(PromptVault.StoredFile file, String mark) {
        // A favourite file (★ Favourite files) is starred.
        FileFavourites.load(dir());
        String safe = FileFavourites.shown(file, file.name == null ? "file" : file.name).replace("&", "&amp;").replace("<", "&lt;");
        // A picture's resolution and aspect ratio ("1920×1080 · 16:9"), a video's aspect ratio (from its preview).
        String dims = lastSize == null || lastSize.isEmpty() ? "" : lastSize + "<br>";
        String sub = dims + (file.shownTitle().replace("&", "&amp;").replace("<", "&lt;")) + " · " + (file.size / 1024) + " KB";
        // Its category and type (and by type or date, its date).
        try {
            sub = sub + "<br>" + FileSort.info(vault(), file, FileSort.current).replace("&", "&amp;").replace("<", "&lt;");
        } catch (Exception ex) {
            // no library: no line
        }
        // A MIDI's length, bars, beats and whether it is a drum MIDI.
        if (lastMidi != null && lastMidi.length() > 0) sub = sub + "<br>" + lastMidi;
        // Its groups (a file can be in several), and In separate storage for a large import.
        FileGroups.load(dir());
        String groups = FileGroups.cardLine(file);
        if (groups.length() > 0) sub = sub + "<br><span style='color:#D9A441'>" + groups.replace("&", "&amp;").replace("<", "&lt;") + "</span>";
        // When it was kept: the newest come first.
        if (file.when().length() > 0) sub = sub + "<br><span style='color:#D9A441'>" + file.when() + "</span>";
        return "<html><center>" + (mark == null ? "" : "<div style='font-size:18px;color:#8A8B86;padding:30px'>" + mark + "</div>")
            + "<div style='width:" + CELL + "px'>" + safe + "</div><div style='font-size:9px;color:#8A8B86'>" + sub + "</div></center></html>";
    }

    /** The file's type in capitals (WAV, MID, MP4), shown where there is no preview. */
    static String mark(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toUpperCase() : "FILE";
    }

    /** The MIDI line of the card made last (MidiInfo.line), "" for any other file. */
    static String lastMidi = "";

    /** The time line of the card playing (mm:ss / mm:ss), its ticker, and whether ❚❚ holds it. */
    private JLabel playingTime;
    private javax.swing.Timer timeTick;
    private boolean clipPaused;

    /** ▶: the card's sound from the start, or on from where ❚❚ left it. */
    void resume(PromptVault.StoredFile file, JButton card, JLabel time) {
        if (this.playingCard == card && this.clip != null) {
            if (this.clipPaused) {
                this.clipPaused = false;
                this.clip.start();
            }
            return;
        }
        this.preview(file, card);
        if (this.playingCard != card || this.clip == null) return;
        this.playingTime = time;
        final javax.sound.sampled.Clip c = this.clip;
        this.timeTick = new javax.swing.Timer(500, e -> {
            if (this.clip != c || this.playingTime == null) return;
            this.playingTime.setText(Playlist.time(c.getMicrosecondPosition() / 1000, c.getMicrosecondLength() / 1000));
        });
        this.timeTick.setInitialDelay(0);
        this.timeTick.start();
    }

    /** ❚❚: the sound held where it is. */
    void pause() {
        if (this.clip == null || this.clipPaused) return;
        this.clipPaused = true;
        this.clip.stop();
    }

    /** The size thumb() read last: "1920×1080 · 16:9" for a picture, "16:9" for a video's preview; "" when not known. */
    static String lastSize = "";

    /** Up to three small thumbnails side by side (reference 1, reference 2, result of version `v`), or null when none is a picture. */
    static ImageIcon slotStrip(PromptVault vault, PromptVault.Version v, int px) {
        if (v == null) return null;
        java.util.List<java.awt.Image> parts = new java.util.ArrayList<java.awt.Image>();
        for (int which = 1; which <= 3; which++) {
            String n = which == 1 ? v.ref1Name : which == 2 ? v.ref2Name : v.resultName;
            if (n == null || n.length() == 0 || vault.sizeOf(v, which) == 0) continue;
            ImageIcon one = thumb(n, vault.fileBytes(v.id, which));
            if (one == null) continue;
            int w = one.getIconWidth(), h = one.getIconHeight();
            double sc = Math.min(px / (double) Math.max(1, w), px / (double) Math.max(1, h));
            parts.add(one.getImage().getScaledInstance(Math.max(1, (int) (w * sc)), Math.max(1, (int) (h * sc)), Image.SCALE_SMOOTH));
        }
        if (parts.isEmpty()) return null;
        java.awt.image.BufferedImage strip = new java.awt.image.BufferedImage(parts.size() * (px + 4), px, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = strip.createGraphics();
        for (int i = 0; i < parts.size(); i++) g.drawImage(parts.get(i), i * (px + 4), 0, null);
        g.dispose();
        return new ImageIcon(strip);
    }

    /** The prompt file preview shown last, for the tests. */
    JDialog lastPromptFilePreview;

    /** A prompt file's text, its prompt's reference and result files (a click: full size) and Edit (no zoom). */
    void promptFilePreview(PromptVault.StoredFile file) {
        PromptVault vault;
        try {
            vault = vault();
        } catch (Exception ex) {
            app.setNow("Could not open the prompt library");
            return;
        }
        final PromptVault.Version owner = vault.versionOf(file);
        final JDialog d = new JDialog(app, file.name, false);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel thumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        if (owner != null) {
            for (int which = 1; which <= 3; which++) {
                final String n = which == 1 ? owner.ref1Name : which == 2 ? owner.ref2Name : owner.resultName;
                if (n == null || n.length() == 0 || vault.sizeOf(owner, which) == 0) continue;
                final byte[] b = vault.fileBytes(owner.id, which);
                ImageIcon icon = thumb(n, b);
                JButton t = new JButton((which == 3 ? "Result" : "Ref " + which) + ": " + n, icon);
                t.setName("prompt-file-preview-thumb:" + which);
                t.setVerticalTextPosition(SwingConstants.BOTTOM);
                t.setHorizontalTextPosition(SwingConstants.CENTER);
                t.addActionListener(e -> this.preview(n, b));
                thumbs.add(t);
            }
        }
        if (thumbs.getComponentCount() > 0) body.add(thumbs, BorderLayout.NORTH);
        javax.swing.JTextArea text = new javax.swing.JTextArea(new String(vault.fileBytes(file.versionId, file.which), java.nio.charset.StandardCharsets.UTF_8));
        text.setName("prompt-file-text");
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(560, 360));
        body.add(scroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        if (owner != null) {
            JButton edit = new JButton("Edit");
            edit.setName("prompt-file-edit");
            edit.addActionListener(e -> {
                d.dispose();
                this.editVersion(owner);
            });
            bottom.add(edit);
        }
        JButton close = new JButton("Close");
        close.addActionListener(e -> d.dispose());
        bottom.add(close);
        body.add(bottom, BorderLayout.SOUTH);
        d.setContentPane(body);
        d.pack();
        d.setLocationRelativeTo(app);
        this.lastPromptFilePreview = d;
        d.setVisible(true);
    }

    /** Edit: the prompt the file belongs to, open on the Prompts page at that version (every field). */
    void editVersion(PromptVault.Version v) {
        if (this.lastGallery != null) this.lastGallery.dispose();
        app.showView("prompts");
        PromptsPage page = app.promptsPage;
        page.openPrompt(v.promptId);
        page.loadVersion(v.id);
    }

    /** A picture's preview, scaled into the cell; null for anything Java cannot read as a picture. Its size: lastSize. */
    static ImageIcon thumb(String name, byte[] bytes) {
        if (bytes == null) return null;
        // A video shows the preview the browser made of its first frame (Make video previews), when there is one.
        if (previewKind(name, bytes) == 3) {
            byte[] made = ThumbCache.get(bytes);
            ImageIcon icon = made == null ? null : thumb("preview.jpg", made);
            String full = lastSize;
            int dot = full.indexOf(" \u00b7 ");
            lastSize = dot >= 0 ? full.substring(dot + 3) : "";
            return icon;
        }
        if (!name.toLowerCase().matches(".+\\.(png|jpe?g|gif|bmp)")) return null;
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img == null) return null;
            lastSize = MediaSize.label(img.getWidth(), img.getHeight());
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
        this.shownScroll = scroll;
        final int at = this.restoreScroll;
        this.restoreScroll = -1;
        if (at >= 0) SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(at));
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
        this.gallery(results ? 1 : 0);
    }

    /** The galleries: 0 Ref files, 1 Result files, 2 Prompt files (the sheets runs saved with --saveprompt). */
    void gallery(final int kind) {
        FileFilter.use(FileFilter.GALLERY);
        final boolean results = kind == 1;
        final JDialog dialog = new JDialog(app, (kind == 2 ? "Prompt files" : results ? "Result files" : "Ref files") + FileFilter.title(), true);
        JPanel kinds = this.galleryTabs(kind, dialog);
        // Sort by type, date or size: the gallery opens again in that order (kept for next time).
        FileSort.current = FileSort.valid(readSort());
        javax.swing.JComboBox<String> sort = new javax.swing.JComboBox<String>(FileSort.CHOICES);
        sort.setName("gallery-sort");
        sort.setSelectedItem(FileSort.current);
        sort.addActionListener(e -> {
            String picked = (String) sort.getSelectedItem();
            if (picked == null || picked.equals(FileSort.current)) return;
            FileSort.current = picked;
            writeSort(picked);
            dialog.dispose();
            this.gallery(kind);
        });
        JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sortRow.add(new JLabel("Sort by"));
        sortRow.add(sort);
        // A I V S T: all files, or only pictures, videos, sound files or text files.
        sortRow.add(javax.swing.Box.createHorizontalStrut(12));
        galleryFilter = FileFilter.normal(galleryFilter);
        final Runnable againGallery = () -> {
            dialog.dispose();
            this.gallery(kind);
        };
        for (final String letter : withGroup()) {
            if (FileFilter.isToggle(letter)) continue;
            final javax.swing.JToggleButton b = new javax.swing.JToggleButton(letter, letter.equals(galleryFilter));
            b.setName("gallery-filter:" + letter);
            b.setToolTipText(DbFilter.label(letter));
            b.addActionListener(e -> {
                galleryFilter = letter;
                againGallery.run();
            });
            sortRow.add(b);
        }
        // F G C: switches with the letter (a right click: G the groups, C the categories).
        FilterBar.toggles(app, sortRow, "gallery-filter:", galleryFilter, againGallery);
        // T1 T2 T3: the files with any of the tags picked.
        final JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        FilterBar.tags(tagRow, "gallery-filter:", againGallery);
        PromptVault lib = null;
        try {
            lib = vault();
        } catch (Exception ex) {
            // by category then sorts by name
        }
        List<PromptVault.StoredFile> every = kind == 2 ? promptFiles() : stored(results);
        List<PromptVault.StoredFile> kept = new ArrayList<PromptVault.StoredFile>();
        FileFavourites.load(dir());
        MediaTags.load(dir());
        kept.addAll(FileFilter.keep(every, lib, galleryFilter));
        // Category tabs: All, the main categories the files' prompts are in, No category.
        final PromptVault tabsLib = lib;
        List<long[]> tabs = FileFavourites.tabs(lib, kept);
        boolean tabThere = false;
        for (long[] t : tabs) tabThere |= t[0] == galleryCategory;
        if (!tabThere) galleryCategory = FileFavourites.ALL;
        JPanel catRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        // F and G show every favourite or group file, whatever its category: no category tabs then.
        final boolean marks = FileFilter.active() || FileFilter.isToggle(galleryFilter);
        if (tabs.size() > 2 && !marks) {
            for (long[] t : tabs) {
                final long tab = t[0];
                String tabName = FileFavourites.tabName(tabsLib, tab);
                javax.swing.JToggleButton b = new javax.swing.JToggleButton(tabName, tab == galleryCategory);
                b.setName("gallery-cat:" + tabName);
                b.addActionListener(e -> {
                    galleryCategory = tab;
                    dialog.dispose();
                    this.gallery(kind);
                });
                catRow.add(b);
            }
        }
        // ★ Favourite files: this gallery's favourites in a list; picking one opens it.
        FileFavourites.load(dir());
        final List<PromptVault.StoredFile> favFiles = FileFavourites.among(every);
        if (!favFiles.isEmpty()) {
            String[] labels = new String[favFiles.size() + 1];
            labels[0] = "\u2605 Favourite files (" + favFiles.size() + ")";
            for (int i = 0; i < favFiles.size(); i++) labels[i + 1] = favFiles.get(i).name + " \u00b7 " + favFiles.get(i).shownTitle();
            final javax.swing.JComboBox<String> favs = new javax.swing.JComboBox<String>(labels);
            favs.setName("gallery-favs");
            // Each with a small thumbnail (read in the background).
            final java.util.Map<Integer, javax.swing.Icon> icons = new java.util.concurrent.ConcurrentHashMap<Integer, javax.swing.Icon>();
            favs.setRenderer(new javax.swing.DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
                    JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                    javax.swing.Icon icon = index > 0 ? icons.get(index) : null;
                    l.setIcon(icon);
                    if (index > 0 && icon == null) l.setIconTextGap(4);
                    return l;
                }
            });
            this.favIcons = icons;
            final PromptVault favLib = lib;
            Thread small = new Thread(() -> {
                PromptVault.quiet(true);
                try {
                    for (int i = 0; i < favFiles.size(); i++) {
                        PromptVault.StoredFile f = favFiles.get(i);
                        ImageIcon big = favLib == null ? null : thumb(f.name, favLib.fileBytes(f.versionId, f.which));
                        if (big == null) continue;
                        java.awt.Image img = big.getImage();
                        int w = img.getWidth(null), h = img.getHeight(null);
                        double k = 40.0 / Math.max(1, Math.max(w, h));
                        icons.put(i + 1, new ImageIcon(img.getScaledInstance(Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)), java.awt.Image.SCALE_SMOOTH)));
                        SwingUtilities.invokeLater(favs::repaint);
                    }
                } catch (Throwable ex) {
                    // the names stay without their thumbnails
                }
            }, "pulsekit-fav-thumbs");
            small.setDaemon(true);
            small.start();
            favs.addActionListener(e -> {
                int i = favs.getSelectedIndex();
                if (i <= 0) return;
                favs.setSelectedIndex(0);
                this.preview(favFiles.get(i - 1), new JButton());
            });
            catRow.add(javax.swing.Box.createHorizontalStrut(12));
            catRow.add(favs);
        }
        boolean third = catRow.getComponentCount() > 0;
        JPanel top = new JPanel(new GridLayout((kinds == null ? 2 : 3) + (third ? 1 : 0), 1, 0, 6));
        if (kinds != null) top.add(kinds);
        top.add(sortRow);
        top.add(tagRow);
        if (third) top.add(catRow);
        List<PromptVault.StoredFile> list = FileSort.sorted(FileFavourites.onTab(lib, kept, marks ? FileFavourites.ALL : galleryCategory), FileSort.current, lib);
        JPanel grid = this.grid(list, (file, card) -> this.preview(file, card),
            (file, card) -> this.menu(file, card, () -> {
                dialog.dispose();
                this.gallery(kind);
            }), "gallery:");
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        // Something added to (or changed in) the library while the gallery is open (a run's result):
        // the gallery opens again with it (not while a preview is open over it).
        this.galleryList = list;
        this.galleryBuilt = PromptVault.changes;
        final javax.swing.Timer watch = new javax.swing.Timer(1500, null);
        watch.addActionListener(e -> {
            if (this.refreshGallery(dialog, kind)) watch.stop();
        });
        watch.start();
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                watch.stop();
                PromptDb.this.stop();
            }
        });
        this.lastGallery = dialog;
        this.show(dialog, this.withPreviews(top, list, dialog, () -> this.gallery(kind)), grid, close);
    }

    /** Ref files (n) | Result files (n) | Prompt files (n): the one shown is greyed; another opens in its place, where it was left. */
    private JPanel galleryTabs(final int kind, final JDialog dialog) {
        JPanel tabs = new JPanel(new GridLayout(1, 3, 8, 0));
        String[] names = {"Ref files", "Result files", "Prompt files"};
        for (int k = 0; k < 3; k++) {
            final int to = k;
            int n = k == 2 ? promptFiles().size() : stored(k == 1).size();
            JButton tab = new JButton(names[k] + " (" + n + ")");
            tab.setName("gallery-tab:" + k);
            tab.setEnabled(k != kind);
            tab.addActionListener(e -> {
                galleryScroll[kind] = this.scrolledTo();
                this.restoreScroll = galleryScroll[to];
                dialog.dispose();
                this.gallery(to);
            });
            tabs.add(tab);
        }
        return tabs;
    }

    /** Where each tab was left: Browse DB's Ref files / Result files, and the galleries' three. */
    static final int[] browseScroll = new int[2];
    static final int[] galleryScroll = new int[3];

    /** The grid's scroll shown last; the place the next one opens at (-1: its own default). */
    JScrollPane shownScroll;
    int restoreScroll = -1;

    /** How far down the grid shown last is scrolled. */
    int scrolledTo() {
        return this.shownScroll == null ? 0 : this.shownScroll.getVerticalScrollBar().getValue();
    }

    /** The name of the file Browse DB picked last: it opens scrolled to it, outlined. */
    static String lastPicked;

    /** The galleries' A I V S T choice (all files at first), and category tab (FileFavourites.ALL at first). */
    static String galleryFilter = "A";
    static long galleryCategory = FileFavourites.ALL;

    /** The library's change count (PromptVault.changes) the gallery shown last was built at. */
    long galleryBuilt;

    /** The gallery shown, out of date (the library changed): opened again. True when it was. */
    boolean refreshGallery(JDialog dialog, boolean results) {
        return this.refreshGallery(dialog, results ? 1 : 0);
    }

    boolean refreshGallery(JDialog dialog, int kind) {
        if (!dialog.isShowing() || this.lastPreview != null && this.lastPreview.isShowing()) return false;
        if (PromptVault.changes == this.galleryBuilt) return false;
        dialog.dispose();
        SwingUtilities.invokeLater(() -> this.gallery(kind));
        return true;
    }

    /** Sort by as last chosen (~/.pulsekit/file-sort.txt), or null. */
    static String readSort() {
        try {
            File f = new File(dir(), "file-sort.txt");
            return f.isFile() ? new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    static void writeSort(String choice) {
        try {
            Files.write(new File(dir(), "file-sort.txt").toPath(), (choice + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ex) {
            // Kept for this run only.
        }
    }

    /** A card's click: a sound (WAV, MP3) or a MIDI (with the kit) plays in place, again stops; anything else opens. */
    void preview(PromptVault.StoredFile file, JButton card) {
        // A prompt file: its text, its prompt's files and Edit.
        if (file.which == PromptVault.PROMPT_FILE) {
            this.stop();
            this.promptFilePreview(file);
            return;
        }
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
            // A picture can go on to the gallery's next or previous one.
            this.previewing = file;
            try {
                this.preview(file.name, bytes);
            } finally {
                this.previewing = null;
            }
            return;
        }
        byte[] raw = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            raw[2 * i] = (byte) pcm[i];
            raw[2 * i + 1] = (byte) (pcm[i] >> 8);
        }
        try {
            final javax.sound.sampled.Clip c = clips.call();
            c.open(new javax.sound.sampled.AudioFormat(sr, 16, 1, true, false), raw, 0, raw.length);
            c.addLineListener(ev -> {
                if (ev.getType() == javax.sound.sampled.LineEvent.Type.STOP) SwingUtilities.invokeLater(() -> {
                    // ❚❚ stops the line too: the sound is kept for ▶.
                    if (this.clip == c && !this.clipPaused) this.stop();
                });
            });
            this.clip = c;
            this.playingCard = card;
            if (card.getClientProperty("play") != null) card.setText(STOP);
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
        this.clipPaused = false;
        if (this.timeTick != null) this.timeTick.stop();
        this.timeTick = null;
        if (this.playingTime != null) this.playingTime.setText(" ");
        this.playingTime = null;
        if (c != null) {
            try {
                c.stop();
                c.close();
            } catch (Exception ignored) {
                // closed already
            }
            // "Playing ..." is no longer true.
            String now = app.nowPlaying.getText();
            if (now != null && now.startsWith("Playing ")) app.setNow(null);
        }
        if (this.playingCard != null) {
            if (this.playingCard.getClientProperty("play") != null) this.playingCard.setText(PLAY);
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

    /** The right-click menu: Open, Save as, Extract prompt (a text file with a **Prompt:**), Strip headers (lines that start with **...**), Rename, Delete. `changed` shows the gallery again. */
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
        // Export to disk: a copy to work on with another program.
        JMenuItem save = new JMenuItem(MediaDir.EXPORT);
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
        // A prompt file: Add to favourites (its prompt version) and Edit.
        PromptVault.Version owner = null;
        try {
            owner = file.which == PromptVault.PROMPT_FILE ? vault().versionOf(file) : null;
        } catch (Exception ex) {
            owner = null;
        }
        if (owner != null) {
            final PromptVault.Version v = owner;
            VersionFavourites.load(dir());
            final boolean fav = VersionFavourites.is(v.id);
            JMenuItem star = new JMenuItem(fav ? "Remove from favourites" : "Add to favourites");
            star.setName("prompt-file-favourite");
            star.addActionListener(e -> {
                VersionFavourites.toggle(v.id);
                try {
                    app.setNow((fav ? "Removed " : "Added ") + VersionFavourites.label(vault(), v) + (fav ? " from favourites" : " to favourites"));
                } catch (Exception ex) {
                    // said nothing
                }
                changed.run();
            });
            JMenuItem edit = new JMenuItem("Edit");
            edit.setName("prompt-file-edit");
            edit.addActionListener(e -> this.editVersion(v));
            menu.add(star);
            menu.add(edit);
        }
        // The file itself among the favourite files (★ Favourite files above the gallery).
        FileFavourites.load(dir());
        final boolean favFile = FileFavourites.is(file);
        JMenuItem favItem = new JMenuItem(favFile ? FileFavourites.REMOVE : FileFavourites.ADD);
        favItem.setName("gallery-favourite-file");
        favItem.addActionListener(e -> {
            FileFavourites.toggle(file);
            app.setNow(file.name + (favFile ? " is no longer a favourite file" : " is a favourite file"));
            changed.run();
        });
        menu.add(favItem);
        menu.add(GroupMenus.item(app, card, FileGroups.keys(file), file.name, changed));
        menu.add(PlaylistWindow.addMenu(app, Playlist.dbAddress(file), file.name));
        menu.add(PlaylistWindow.addDefaultItem(app, Playlist.dbAddress(file), file.name));
        // Tags… and Category…, as the Media browser has them (the file by its name and size).
        JMenuItem tagsItem = new JMenuItem(MediaTags.TAGS);
        tagsItem.setName("gallery-tags");
        tagsItem.addActionListener(e -> TagDialogs.tags(app, card, file.name, file.size, null));
        menu.add(tagsItem);
        JMenuItem catItem = new JMenuItem(MediaTags.CATEGORY);
        catItem.setName("gallery-category");
        catItem.addActionListener(e -> TagDialogs.category(app, card, file.name, file.size, null));
        menu.add(catItem);
        menu.add(open);
        menu.add(save);
        // Send to zip: the file into a zip (a password recommended), saved where the save dialog says.
        JMenuItem zip = new JMenuItem(ZipPack.SEND);
        zip.setName("gallery-send-zip");
        zip.addActionListener(e -> {
            try {
                ZipFlow.sendLibraryFile(app, vault(), file);
            } catch (Exception ex) {
                app.setNow("Could not zip " + file.name);
            }
        });
        menu.add(zip);
        boolean extractable = false;
        try {
            extractable = PromptExtract.offered(vault(), file.versionId, file.which, file.name);
        } catch (Exception ex) {
            // No library: no Extract prompt.
        }
        if (extractable) {
            // A text file with a **Prompt:** (a SogniChat answer.txt): the text after its **Prompt:** as a new file of the same kind.
            JMenuItem extract = new JMenuItem("Extract prompt");
            extract.addActionListener(e -> {
                try {
                    String made = PromptExtract.keep(vault(), file.versionId, file.which, file.name, file.promptTitle);
                    app.setNow("Extracted the prompt of " + file.name + " as " + made);
                    changed.run();
                } catch (Exception ex) {
                    app.setNow(ex.getMessage() != null ? ex.getMessage() : "Could not extract the prompt of " + file.name);
                }
            });
            menu.add(extract);
        }
        boolean strippable = false;
        try {
            strippable = PromptExtract.stripOffered(vault(), file.versionId, file.which, file.name);
        } catch (Exception ex) {
            // No library: no Strip headers.
        }
        if (strippable) {
            // Lines that start with **Header:**: the text without them as <file> noheaders.txt.
            JMenuItem strip = new JMenuItem("Strip headers");
            strip.addActionListener(e -> {
                try {
                    String made = PromptExtract.keepStripped(vault(), file.versionId, file.which, file.name, file.promptTitle);
                    app.setNow("Stripped the headers of " + file.name + " as " + made);
                    changed.run();
                } catch (Exception ex) {
                    app.setNow(ex.getMessage() != null ? ex.getMessage() : "Could not strip the headers of " + file.name);
                }
            });
            menu.add(strip);
        }
        // A picture, known by its name or, without a picture's extension, by its bytes.
        if (previewKind(file.name, this.bytesOf(file)) == 2) {
            // Upscale/Resize image: ImageUpscaler on PyJav with this picture as its input; Return comes back to Prompts.
            JMenuItem upscale = new JMenuItem("Upscale/Resize image");
            upscale.addActionListener(e -> this.upscale(file));
            menu.add(upscale);
        }
        // A MIDI: the MIDI editor, Render with SF2 or plugin, Play with default SoundFont; a sound: Convert drum hits into MIDI.
        for (final String kindItem : MediaDir.kindItems(file.name, true)) {
            JMenuItem k = new JMenuItem(kindItem);
            k.setName("file-menu:" + kindItem);
            k.addActionListener(e -> this.kindItem(file, kindItem));
            menu.add(k);
        }
        menu.add(rename);
        menu.add(delete);
        this.lastFileMenu = menu;
        MenuCancel.add(menu);
        if (card.isShowing()) menu.show(card, card.getWidth() / 2, card.getHeight() / 2);
    }

    /** A stored file's bytes; none when the library cannot be read. */
    private byte[] bytesOf(PromptVault.StoredFile file) {
        try {
            return vault().fileBytes(file.versionId, file.which);
        } catch (Exception ex) {
            return new byte[0];
        }
    }

    /** Upscale/Resize image: the stored picture as a file in PyJav's input folder, ImageUpscaler with it as its input, its Params open. */
    void upscale(PromptVault.StoredFile file) {
        try {
            byte[] bytes = vault().fileBytes(file.versionId, file.which);
            File dir = new File(dir(), "pyjav-in");
            dir.mkdirs();
            File in = new File(dir, PromptVault.fileTitle(file.name).replaceAll("[\\\\/:*?\"<>|]", "_"));
            Files.write(in.toPath(), bytes);
            java.awt.Window open = javax.swing.SwingUtilities.getWindowAncestor(this.lastFileMenu == null ? null : this.lastFileMenu.getInvoker());
            if (open instanceof JDialog) open.dispose();
            app.showView("py");
            app.programMenus.selectListedProgram("Java", "ImageUpscaler.java");
            app.pyJav.setInputFile(in);
            app.pyJav.setReturn("Return to Prompts", () -> app.showView("prompts"));
            SwingUtilities.invokeLater(() -> app.pyJav.openParams());
            app.setNow("ImageUpscaler: " + file.name);
        } catch (Exception ex) {
            app.setNow("Could not upscale " + file.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        }
    }

    /**
     * A MIDI or sound file of the library, as the Media browser's items do it: the MIDI editor (Save
     * keeps the edited MIDI as a new file of the same gallery, <name>-edited.mid), Render with SF2
     * (Midi2Wav), Render with plugin (vst_render.py) or Convert drum hits into MIDI (DrumMidi_CRT)
     * on PyJav with it as the input (Return comes back to Prompts), or Play with default SoundFont.
     */
    void kindItem(final PromptVault.StoredFile file, String item) {
        try {
            byte[] bytes = vault().fileBytes(file.versionId, file.which);
            if (MediaDir.MIDI_EDIT.equals(item)) {
                final String edited = PromptVault.fileTitle(file.name).replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + "-edited.mid";
                MidiEditScreen.openFile(app, file.name, bytes, midi -> {
                    vault().addLibraryFile(edited, midi, "Edited", file.which == 3 ? 3 : 1);
                    return edited + " in " + (file.which == 3 ? "Result files" : "Ref files");
                });
                return;
            }
            File dir = new File(dir(), "pyjav-in");
            dir.mkdirs();
            File in = new File(dir, PromptVault.fileTitle(file.name).replaceAll("[\\\\/:*?\"<>|]", "_"));
            Files.write(in.toPath(), bytes);
            if (MediaDir.PLAY_SF.equals(item)) {
                MediaBrowser.playDefault(app, in, file.name);
                return;
            }
            if (MediaDir.TO_FILE_SET.equals(item)) {
                MediaBrowser.toFileSet(app, in, file.name);
                return;
            }
            if (MediaDir.TO_WAV.equals(item)) {
                // The WAV a new file of the same gallery.
                AudioIo.Pcm pcm = Mp3Decode.parse(bytes);
                String made = PromptVault.fileTitle(file.name).replaceAll("(?i)\\.mp3$", "") + ".wav";
                vault().addLibraryFile(made, AudioIo.encodeWav(AudioIo.floatsToShorts(pcm.samples), pcm.sr), "Converted", file.which == 3 ? 3 : 1);
                app.setNow(made + " in " + (file.which == 3 ? "Result files" : "Ref files"));
                return;
            }
            if (MediaDir.TO_COMPOSER.equals(item)) {
                MediaBrowser.toComposer(app, in, file.name);
                return;
            }
            java.awt.Window open = javax.swing.SwingUtilities.getWindowAncestor(this.lastFileMenu == null ? null : this.lastFileMenu.getInvoker());
            if (open instanceof JDialog) open.dispose();
            app.showView("py");
            if (MediaDir.RENDER_PLUGIN.equals(item)) app.programMenus.selectListedProgram("Python", "vst_render.py");
            else app.programMenus.selectListedProgram("Java", MediaDir.TO_MIDI.equals(item) ? "DrumMidi_CRT.jar" : "Midi2Wav.java");
            app.pyJav.setInputFile(in);
            app.pyJav.setReturn("Return to Prompts", () -> app.showView("prompts"));
            SwingUtilities.invokeLater(() -> app.pyJav.openParams());
            app.setNow(item + ": " + file.name);
        } catch (Exception ex) {
            app.setNow(item + " failed for " + file.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        }
    }

    /** The right-click menu shown last, for the tests. */
    JPopupMenu lastFileMenu;

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
            PromptVault vault = vault();
            PromptVault.Version version = vault.selectedRefs(title, category, n1, n2);
            if (version == null) {
                refs.note = "Reference file 1: none\nReference file 2: none";
                return refs;
            }
            File dir = new File(dir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            String name1 = version.ref1Name == null ? "" : version.ref1Name;
            String name2 = version.ref2Name == null ? "" : version.ref2Name;
            refs.description = version.description == null ? "" : version.description;
            refs.resultName = vault.sizeOf(version, 3) > 0 && version.resultName != null ? version.resultName : "";
            if (vault.sizeOf(version, 1) > 0) refs.ref1Path = write(dir, "ref1-", name1, vault.bytesOf(version, 1));
            if (vault.sizeOf(version, 2) > 0) refs.ref2Path = write(dir, "ref2-", name2, vault.bytesOf(version, 2));
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
            if (vault.sizeOf(version, 3) > 0 && version.resultName != null && version.resultName.length() > 0) return version.resultName;
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
        // The Prompts page's player: it opens as its last video was left (Loop video, volume, zoom).
        loadPromptVideo();
        this.promptsPlayer = true;
        try {
            this.videoFile(name, tmp, PromptVideo.loop, false);
        } finally {
            this.promptsPlayer = false;
        }
    }

    /** True while the Prompts page opens a video (its player keeps PromptVideo's settings). */
    boolean promptsPlayer;

    /** The Prompts page's video settings, kept in ~/.pulsekit/prompt-video.txt. */
    static File promptVideoFile() {
        return new File(dir(), "prompt-video.txt");
    }

    static void loadPromptVideo() {
        try {
            File f = promptVideoFile();
            PromptVideo.decode(f.isFile() ? new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        } catch (Exception ex) {
            PromptVideo.decode(null);
        }
    }

    static void savePromptVideo() {
        try {
            Files.write(promptVideoFile().toPath(), PromptVideo.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // kept for this session
        }
    }

    /**
     * As above, for a video file as it is (the Media browser's), looping at its end when `loop` says
     * so; with `remember` it opens at the Media browser's last volume, zoom and speed, and keeps them.
     */
    void videoFile(String name, File tmp, boolean loop, boolean remember) {
        // With VLC on the computer the video plays here, in the Preview window.
        if (VlcPlayer.available()) {
            try {
                this.vlcPreview(name, tmp, loop, remember);
                return;
            } catch (Exception ex) {
                app.setNow(ex.getMessage() != null ? ex.getMessage() : "VLC could not play " + name);
            }
        }
        Object[] options = new Object[] {"Play", "Open", "Extract frames", "Close"};
        int ans = JOptionPane.showOptionDialog(app, name + " (" + Math.max(1, tmp.length() / 1024) + " KB)\n\n"
            + "Play opens it in the browser with Play/Pause, Stop, Mute and volume.\n"
            + "Extract frames keeps its first and last frame as reference files (the browser reads them).\n\n"
            + "To play videos here in Pulsekit, install VLC (videolan.org).", "Preview \u00b7 " + name,
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        try {
            if (ans == 0) browser.accept((remember ? app.pyJav.videoPage(tmp, loop, MediaDir.volume, MediaDir.zoom, MediaDir.speed)
                : this.promptsPlayer ? app.pyJav.videoPage(tmp, loop, PromptVideo.volume, MediaDir.clampZoom(PromptVideo.zoom), 1) : app.pyJav.videoPage(tmp, loop)).toURI());
            else if (ans == 1) java.awt.Desktop.getDesktop().open(tmp);
            else if (ans == 2) this.extractFrames(name, tmp);
        } catch (Exception ex) {
            app.setNow("Could not open a player for " + name);
        }
    }

    /** The VLC preview shown last, and its player, for the tests. */
    JDialog lastVideo;
    VlcPlayer lastPlayer;

    /**
     * The video in the Preview window, played by VLC: Play/Pause, Stop, Mute, volume and a
     * position slider; Open in browser, Extract frames and Close below. Closing stops it.
     */
    void vlcPreview(String name, File video) {
        this.vlcPreview(name, video, false, false);
    }

    /** The video paused, and its menu: Save frame (the Prompts page's: into the library), Extract frame, Capture frame and process, the video's own items. */
    void frameMenu(String name, File video, VlcPlayer player, boolean prompts, int x, int y) {
        if (player.playing()) player.pause();
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        if (prompts) {
            javax.swing.JMenuItem keep = new javax.swing.JMenuItem("Save frame");
            keep.setName("video-save-frame");
            keep.addActionListener(ev -> this.saveFrame(name, player));
            m.add(keep);
        }
        javax.swing.JMenuItem extract = new javax.swing.JMenuItem("Extract frame");
        extract.setName("video-extract-frame");
        extract.addActionListener(ev -> PromptDb.this.captureFrame(name, video, player));
        m.add(extract);
        // The frame captured, then into a program as its Reference file (as Picture ready's Process as a reference).
        javax.swing.JMenuItem process = new javax.swing.JMenuItem("Capture frame and process as a reference");
        process.setName("video-process-frame");
        process.addActionListener(ev -> {
            PromptDb.this.lastCapture = null;
            PromptDb.this.captureFrame(name, video, player);
            final File shot = PromptDb.this.lastCapture;
            if (shot == null) return;
            java.awt.Window w = SwingUtilities.getWindowAncestor(player.screen);
            if (w != null) w.dispose();
            SwingUtilities.invokeLater(() -> ProcessRefDialog.open(app, shot));
        });
        m.add(process);
        // The video among the favourites, or in a group.
        for (java.awt.Component c : GroupMenus.fileMenu(app, video).getComponents()) if (c instanceof JMenuItem && !MenuCancel.is(c)) m.add((JMenuItem) c);
        PromptDb.this.lastFrameMenu = m;
        MenuCancel.add(m);
        if (player.screen.isShowing()) m.show(player.screen, x, y);
    }

    /** Save frame: the frame shown kept in the library as a reference file (<video>-1m02s345.png; a linked video's too). */
    void saveFrame(String name, VlcPlayer player) {
        java.awt.image.BufferedImage img = player.screen.frame;
        if (img == null) {
            app.setNow("No frame to save yet");
            return;
        }
        try {
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", png);
            String file = MediaDir.frameName(name, player.timeMs());
            String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
            vault().addReferenceImage(file, png.toByteArray(), stem, 1);
            lastSavedFrame = file;
            app.setNow("Saved " + file + " in Ref files");
        } catch (Exception ex) {
            app.setNow("Could not save the frame: " + ex.getMessage());
        }
    }

    /** The frame Save frame kept last (for the tests). */
    String lastSavedFrame;

    /** The right click's menu shown last (Extract frame), for the tests. */
    javax.swing.JPopupMenu lastFrameMenu;

    /** The last frame C saved, for the tests. */
    File lastCapture;

    /** C: the frame shown (paused) saved as a PNG in the SC folder beside the video (made when it is not there). */
    void captureFrame(String name, File video, VlcPlayer player) {
        java.awt.image.BufferedImage img = player.screen.frame;
        if (img == null) {
            app.setNow("No frame to capture yet");
            return;
        }
        int vw = player.screen.visibleW;
        int vh = player.screen.visibleH;
        if (vw > 0 && vh > 0 && (vw < img.getWidth() || vh < img.getHeight())) img = img.getSubimage(0, 0, Math.min(vw, img.getWidth()), Math.min(vh, img.getHeight()));
        File folder = new File(video.getAbsoluteFile().getParentFile(), MediaDir.FRAMES_DIR);
        try {
            if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("could not make " + folder.getPath());
            String file = MediaDir.frameName(name, player.timeMs());
            String stem = file.replaceAll("\\.png$", "");
            File out = new File(folder, file);
            for (int n = 1; out.exists(); n++) out = new File(folder, stem + " (" + n + ").png");
            javax.imageio.ImageIO.write(img, "png", out);
            this.lastCapture = out;
            app.setNow("Saved the frame as " + out.getPath());
        } catch (Exception ex) {
            app.setNow("Could not capture the frame: " + ex.getMessage());
        }
    }

    /** Media browser: the speed picked (or the remembered one), for the tests. */
    javax.swing.JComboBox<String> lastSpeed;

    /** As above; with `loop` (the Media browser's Loop videos) the Loop box starts ticked: the video starts again at its end. */
    void vlcPreview(String name, File video, boolean loop, final boolean remember) {
        final VlcPlayer player = new VlcPlayer(video);
        // The Media browser's player and the Prompts page's open as their last video was left; others at 80%, fit, 1x.
        final boolean prompts = this.promptsPlayer && !remember;
        final int startVolume = remember ? MediaDir.volume : prompts ? PromptVideo.volume : 80;
        final JDialog dialog = new JDialog(app, "Preview \u00b7 " + name, true);
        JButton play = new JButton("Play");
        play.setName("video-play");
        JButton stop = new JButton("Stop");
        stop.setName("video-stop");
        JButton mute = new JButton("Mute");
        mute.setName("video-mute");
        javax.swing.JSlider volume = new javax.swing.JSlider(0, 100, startVolume);
        volume.setName("video-volume");
        volume.setPreferredSize(new Dimension(120, volume.getPreferredSize().height));
        JLabel level = new JLabel(startVolume + "%");
        javax.swing.JSlider position = new javax.swing.JSlider(0, 1000, 0);
        position.setName("video-position");
        JLabel time = new JLabel("0:00 / 0:00");
        time.setName("video-time");
        final boolean[] muted = new boolean[1];
        // The speed VLC plays at now (1 until it plays).
        final double[] rate = new double[] {1};
        final boolean[] dragging = new boolean[1];
        player.volume(startVolume);
        // Play, Pause and Stop each always shown (as on the phone); C saves the paused frame.
        // Play while it plays pauses it; Pause while paused plays on.
        play.addActionListener(e -> {
            if (player.playing()) player.pause();
            else player.play();
        });
        JButton pause = new JButton("Pause");
        pause.setName("video-pause");
        pause.addActionListener(e -> {
            if (player.playing()) player.pause();
            else player.play();
        });
        final JButton capture = new JButton("C");
        capture.setName("video-capture");
        capture.setToolTipText("Capture this frame into the SC folder beside the video");
        capture.setVisible(false);
        // C held: the frame captured, then ImageUpscaler with it as the input (Return: the Media browser there).
        final boolean[] heldC = new boolean[1];
        capture.addActionListener(e -> {
            if (heldC[0]) {
                heldC[0] = false;
                return;
            }
            this.captureFrame(name, video, player);
        });
        capture.addMouseListener(new java.awt.event.MouseAdapter() {
            javax.swing.Timer hold;

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                this.hold = new javax.swing.Timer(600, ev -> {
                    heldC[0] = true;
                    PromptDb.this.lastCapture = null;
                    PromptDb.this.captureFrame(name, video, player);
                    File shot = PromptDb.this.lastCapture;
                    if (shot == null) return;
                    java.awt.Window w = SwingUtilities.getWindowAncestor(capture);
                    if (w != null) w.dispose();
                    if (app.mediaBrowser != null && app.mediaBrowser.last != null) app.mediaBrowser.last.dispose();
                    app.showView("py");
                    app.programMenus.selectListedProgram("Java", "ImageUpscaler.java");
                    app.pyJav.setInputFile(shot);
                    final File folder = video.getAbsoluteFile().getParentFile();
                    app.pyJav.setReturn(() -> app.mediaBrowser.open(folder));
                    SwingUtilities.invokeLater(() -> app.pyJav.openParams());
                });
                this.hold.setRepeats(false);
                this.hold.start();
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (this.hold != null) this.hold.stop();
            }
        });
        stop.addActionListener(e -> {
            player.stop();
            position.setValue(0);
        });
        mute.addActionListener(e -> {
            muted[0] = !muted[0];
            player.mute(muted[0]);
            mute.setText(muted[0] ? "Unmute" : "Mute");
            level.setText(muted[0] ? "muted" : volume.getValue() + "%");
        });
        volume.addChangeListener(e -> {
            player.volume(volume.getValue());
            if (!muted[0]) level.setText(volume.getValue() + "%");
        });
        final javax.swing.JCheckBox looping = new javax.swing.JCheckBox("Loop video", loop);
        looping.setName("video-loop");
        // Zoom: − and + by steps, Fit; the mouse wheel too, and a drag moves a zoomed picture.
        JButton zoomOut = new JButton("Zoom \u2212");
        zoomOut.setName("video-zoom-out");
        JButton zoomIn = new JButton("Zoom +");
        zoomIn.setName("video-zoom-in");
        JButton fit = new JButton("Fit");
        fit.setName("video-fit");
        final JLabel zoomLevel = new JLabel("100%");
        zoomLevel.setName("video-zoom");
        player.screen.zoomed = () -> zoomLevel.setText(MediaDir.zoomLabel(player.screen.zoom));
        zoomOut.addActionListener(e -> {
            player.screen.zoomTo(MediaDir.zoom(player.screen.zoom, false));
            player.screen.zoomed.run();
        });
        zoomIn.addActionListener(e -> {
            player.screen.zoomTo(MediaDir.zoom(player.screen.zoom, true));
            player.screen.zoomed.run();
        });
        fit.addActionListener(e -> {
            player.screen.zoomTo(1);
            player.screen.zoomed.run();
        });
        // Playback speed, 0.25x to 2x.
        String[] speeds = new String[MediaDir.SPEEDS.length];
        for (int i = 0; i < speeds.length; i++) speeds[i] = MediaDir.speedLabel(MediaDir.SPEEDS[i]);
        final javax.swing.JComboBox<String> speed = new javax.swing.JComboBox<String>(speeds);
        speed.setName("video-speed");
        speed.setSelectedItem(MediaDir.speedLabel(remember ? MediaDir.speed : 1));

        this.lastSpeed = speed;
        if (remember || prompts) {
            player.screen.zoomTo(remember ? MediaDir.zoom : MediaDir.clampZoom(PromptVideo.zoom));
            player.screen.zoomed.run();
        }
        position.addChangeListener(e -> {
            dragging[0] = position.getValueIsAdjusting();
            if (position.getValueIsAdjusting()) player.seek(position.getValue() / 1000f);
        });
        // Play/Pause, the position and the time follow the player.
        javax.swing.Timer follow = new javax.swing.Timer(200, e -> {
            player.refreshSize();
            boolean on = player.playing();
            // The speed picked, once it plays (VLC takes it only then; a new start forgets it).
            double want = MediaDir.SPEEDS[speed.getSelectedIndex()];
            if (on && rate[0] != want) {
                player.rate(want);
                rate[0] = want;
            } else if (!on && player.state() >= 5) {
                // Stopped or at its end: the next start begins at 1x.
                rate[0] = 1;
            }
            long len = player.lengthMs();
            // C while paused (a frame shown), in the Media browser's player.
            capture.setVisible(remember && !on && player.screen.frame != null && player.state() != 0);
            int state = player.state();
            // Loop: at its end it plays again from the start.
            if (state == VlcPlayer.ENDED && looping.isSelected()) {
                player.stop();
                player.play();
                return;
            }
            long at = state == VlcPlayer.ENDED ? len : player.timeMs();
            time.setText(VlcPlayer.clock(at) + " / " + VlcPlayer.clock(len));
            if (!dragging[0]) position.setValue(state == VlcPlayer.ENDED ? 1000 : Math.round(player.position() * 1000));
        });
        JPanel transport = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        transport.add(stop);
        transport.add(play);
        transport.add(pause);
        transport.add(capture);
        // Mute and volume: in the Media browser's player at the top, shown by a click on the picture.
        final JPanel sound = remember ? new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)) : transport;
        sound.setName("video-sound");
        sound.add(mute);
        sound.add(new JLabel("Volume"));
        sound.add(volume);
        sound.add(level);
        transport.add(looping);
        // Right click (the phone's long press): paused, then Save frame (into the library; the Prompts page's player),
        // Extract frame (into the SC folder beside the video) and the rest.
        player.screen.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (!SwingUtilities.isRightMouseButton(e)) return;
                PromptDb.this.frameMenu(name, video, player, prompts, e.getX(), e.getY());
            }
        });
        if (prompts) {
            // The Prompts page's video: a click plays or pauses; held, it pauses with the menu (as a long press on the phone).
            player.screen.clicked = () -> {
                if (player.playing()) player.pause();
                else player.play();
            };
            player.screen.held = () -> this.frameMenu(name, video, player, true, player.screen.getWidth() / 2, player.screen.getHeight() / 2);
        }
        if (remember) {
            sound.setVisible(false);
            // A click on the picture works as Pause (pauses, or plays on); held, it pauses and brings Mute and the volume to the top.
            player.screen.addMouseListener(new java.awt.event.MouseAdapter() {
                javax.swing.Timer hold;
                boolean held;

                java.awt.Point at;

                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    this.held = false;
                    this.at = null;
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    this.at = e.getPoint();
                    this.hold = new javax.swing.Timer(600, ev -> {
                        this.held = true;
                        if (player.playing()) player.pause();
                        sound.setVisible(!sound.isVisible());
                        sound.getParent().revalidate();
                    });
                    this.hold.setRepeats(false);
                    this.hold.start();
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (this.hold != null) this.hold.stop();
                    // A drag (moving a zoomed video) is not a click.
                    if (this.held || this.at == null || this.at.distance(e.getPoint()) > 6) return;
                    if (player.playing()) player.pause();
                    else player.play();
                }
            });
        }
        JPanel zooms = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        zooms.add(zoomOut);
        zooms.add(zoomIn);
        zooms.add(fit);
        zooms.add(zoomLevel);
        zooms.add(new JLabel("   Speed"));
        zooms.add(speed);
        JPanel seek = new JPanel(new BorderLayout(8, 0));
        seek.add(position, BorderLayout.CENTER);
        seek.add(time, BorderLayout.EAST);
        JPanel controls = new JPanel(new BorderLayout(0, 6));
        controls.add(seek, BorderLayout.NORTH);
        controls.add(transport, BorderLayout.CENTER);
        controls.add(zooms, BorderLayout.SOUTH);
        JButton browser = new JButton("Open in browser");
        browser.addActionListener(e -> {
            player.pause();
            try {
                PromptDb.browser.accept(app.pyJav.videoPage(video, looping.isSelected()).toURI());
            } catch (Exception ex) {
                app.setNow("Could not open a player for " + name);
            }
        });
        JButton frames = new JButton("Extract frames");
        frames.setName("video-frames");
        frames.addActionListener(e -> this.extractFrames(name, video));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        bottom.add(browser);
        bottom.add(frames);
        bottom.add(close);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(controls, BorderLayout.NORTH);
        south.add(bottom, BorderLayout.SOUTH);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        body.add(player.screen, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        if (remember) body.add(sound, BorderLayout.NORTH);
        dialog.setContentPane(body);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                follow.start();
                // It opens on its first frame, paused, as on the phone.
                player.play();
                new javax.swing.Timer(250, ev -> {
                    ((javax.swing.Timer) ev.getSource()).stop();
                    if (dialog.isShowing() && player.playing()) {
                        player.pause();
                        player.seek(0f);
                    }
                }).start();
            }

            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                follow.stop();
                player.release();
                if (remember) {
                    // The Media browser's next video opens as this one was left.
                    MediaDir.volume = volume.getValue();
                    MediaDir.zoom = player.screen.zoom;
                    MediaDir.speed = MediaDir.SPEEDS[speed.getSelectedIndex()];
                    MediaBrowser.saveLoop();
                }
                if (prompts) {
                    // The Prompts page's next video opens as this one was left.
                    PromptVideo.volume = volume.getValue();
                    PromptVideo.zoom = player.screen.zoom;
                    PromptVideo.loop = looping.isSelected();
                    savePromptVideo();
                }
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        this.lastVideo = dialog;
        this.lastPlayer = player;
        dialog.setVisible(true);
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

    /** The files of the gallery shown last, in its order, and the one opened in Preview from it. */
    List<PromptVault.StoredFile> galleryList;
    PromptVault.StoredFile previewing;

    /** The picture `step` places on in the gallery (1 next, -1 previous) from `from`, with its bytes; null at the ends. */
    Object[] stepPicture(PromptVault.StoredFile from, int step) {
        List<PromptVault.StoredFile> list = this.galleryList;
        if (from == null || list == null) return null;
        int at = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).versionId == from.versionId && list.get(i).which == from.which) at = i;
        }
        if (at < 0) return null;
        for (int i = at + step; i >= 0 && i < list.size(); i += step) {
            PromptVault.StoredFile f = list.get(i);
            byte[] bytes;
            try {
                bytes = vault().fileBytes(f.versionId, f.which);
            } catch (Exception ex) {
                continue;
            }
            if (previewKind(f.name, bytes) == 2) return new Object[] {f, bytes};
        }
        return null;
    }

    private void zoomPreview(String name, byte[] bytes, int kind) {
        final JDialog dialog = new JDialog(app, "Preview \u00b7 " + name, true);
        // A picture opens at the zoom the last one had.
        if (kind == 2) loadPromptVideo();
        final float[] zoom = new float[] {kind == 2 ? (float) PromptVideo.imageZoom : 1f};
        final javax.swing.JComponent view;
        final Runnable apply;
        if (kind == 1) {
            javax.swing.JTextArea text = new javax.swing.JTextArea(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), 24, 70);
            text.setName("preview-text");
            text.setEditable(false);
            // Right click or a long press: Select all, Copy (as on the phone).
            TextMenu.attach(text);
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
        JLabel percent = new JLabel(Math.round(zoom[0] * 100) + "%");
        if (zoom[0] != 1f) apply.run();
        JButton out = new JButton("\u2212");
        JButton in = new JButton("+");
        JButton reset = new JButton("100%");
        java.util.function.Consumer<Float> set = next -> {
            zoom[0] = Math.max(0.25f, Math.min(4f, next));
            percent.setText(Math.round(zoom[0] * 100) + "%");
            apply.run();
            view.revalidate();
            if (kind == 2) {
                PromptVideo.imageZoom = zoom[0];
                savePromptVideo();
            }
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
        // From the gallery: Previous / Next (and the arrow keys) show its other pictures.
        final PromptVault.StoredFile from = kind == 2 ? this.previewing : null;
        if (from != null) {
            java.util.function.IntConsumer go = step -> {
                Object[] next = this.stepPicture(from, step);
                if (next == null) {
                    app.setNow(step > 0 ? "The last picture" : "The first picture");
                    return;
                }
                dialog.dispose();
                SwingUtilities.invokeLater(() -> {
                    this.previewing = (PromptVault.StoredFile) next[0];
                    try {
                        this.zoomPreview(((PromptVault.StoredFile) next[0]).name, (byte[]) next[1], 2);
                    } finally {
                        this.previewing = null;
                    }
                });
            };
            JButton prev = new JButton("\u25c0 Previous");
            prev.setName("preview-prev");
            prev.addActionListener(e -> go.accept(-1));
            JButton next = new JButton("Next \u25b6");
            next.setName("preview-next");
            next.addActionListener(e -> go.accept(1));
            top.add(prev);
            top.add(next);
            javax.swing.JRootPane root = dialog.getRootPane();
            root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke("LEFT"), "preview-prev");
            root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke("RIGHT"), "preview-next");
            root.getActionMap().put("preview-prev", new javax.swing.AbstractAction() {
                public void actionPerformed(java.awt.event.ActionEvent e) { go.accept(-1); }
            });
            root.getActionMap().put("preview-next", new javax.swing.AbstractAction() {
                public void actionPerformed(java.awt.event.ActionEvent e) { go.accept(1); }
            });
        }
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
                // No drums for the kit: Java's own instruments play it.
                try {
                    File tmp = File.createTempFile("pulsekit-midi", ".mid");
                    tmp.deleteOnExit();
                    Files.write(tmp.toPath(), bytes);
                    MediaBrowser.playDefault(app, tmp, name);
                } catch (Exception ex) {
                    app.setNow(name + " could not be played");
                }
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
        String note = midi ? "MIDI drums, played with Pulsekit's kit sounds (your imported SoundFont, if any)" : (bytes.length / 1024) + " KB";
        this.soundPlayer(name, note, raw, sr);
    }

    /** The sound player shown last, for the tests. */
    JDialog lastSoundPlayer;

    /** A sound in a small player: Play, Pause, Stop, mm:ss / mm:ss, volume and Mute (the Media browser's volume, kept). */
    void soundPlayer(String name, String note, byte[] raw, float sr) {
        final javax.sound.sampled.Clip c;
        try {
            c = javax.sound.sampled.AudioSystem.getClip();
            c.open(new javax.sound.sampled.AudioFormat(sr, 16, 1, true, false), raw, 0, raw.length);
        } catch (Exception ex) {
            app.setNow("No audio output for " + name);
            return;
        }
        final JDialog d = new JDialog(app, "Preview \u00b7 " + name, true);
        JPanel col = new JPanel(new java.awt.GridLayout(0, 1, 0, 6));
        col.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        col.add(new JLabel(name + " \u00b7 " + note));
        final JLabel time = new JLabel(Playlist.time(0, c.getMicrosecondLength() / 1000));
        time.setName("sound-time");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton play = new JButton("Play");
        play.setName("sound-play");
        JButton pause = new JButton("Pause");
        pause.setName("sound-pause");
        JButton stop = new JButton("Stop");
        stop.setName("sound-stop");
        final JButton mute = new JButton("Mute");
        mute.setName("sound-mute");
        final javax.swing.JSlider volume = new javax.swing.JSlider(0, 100, MediaDir.volume);
        volume.setName("sound-volume");
        final JLabel level = new JLabel(MediaDir.volume + "%");
        final boolean[] muted = new boolean[1];
        final Runnable applyVolume = () -> {
            int v = muted[0] ? 0 : volume.getValue();
            level.setText(muted[0] ? "muted" : v + "%");
            try {
                javax.sound.sampled.FloatControl gain = (javax.sound.sampled.FloatControl) c.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                float db = v <= 0 ? gain.getMinimum() : (float) (20 * Math.log10(v / 100.0));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
            } catch (Exception ex) {
                // no volume control on this output
            }
        };
        applyVolume.run();
        volume.addChangeListener(e -> {
            MediaDir.volume = volume.getValue();
            applyVolume.run();
        });
        mute.addActionListener(e -> {
            muted[0] = !muted[0];
            mute.setText(muted[0] ? "Unmute" : "Mute");
            applyVolume.run();
        });
        play.addActionListener(e -> {
            if (c.getFramePosition() >= c.getFrameLength()) c.setFramePosition(0);
            c.start();
        });
        pause.addActionListener(e -> c.stop());
        stop.addActionListener(e -> {
            c.stop();
            c.setFramePosition(0);
        });
        buttons.add(play);
        buttons.add(pause);
        buttons.add(stop);
        buttons.add(time);
        col.add(buttons);
        JPanel vol = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        vol.add(new JLabel("Volume"));
        vol.add(volume);
        vol.add(level);
        vol.add(mute);
        col.add(vol);
        JButton close = new JButton("Close");
        close.addActionListener(e -> d.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        south.add(close);
        d.getContentPane().add(col, BorderLayout.CENTER);
        d.getContentPane().add(south, BorderLayout.SOUTH);
        final javax.swing.Timer tick = new javax.swing.Timer(250, e -> time.setText(Playlist.time(c.getMicrosecondPosition() / 1000, c.getMicrosecondLength() / 1000)));
        tick.start();
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                tick.stop();
                c.stop();
                c.close();
                MediaBrowser.saveLoop();
            }
        });
        d.pack();
        d.setLocationRelativeTo(app);
        lastSoundPlayer = d;
        c.start();
        d.setVisible(true);
    }

    // ------------------------------------------------------------ File > Import as Ref file / Result file

    void importFile(boolean result) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(result ? "Import as Result file" : "Import as Ref file");
        if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
        this.importPicked(chooser.getSelectedFile(), result);
    }

    /** Asks to add the file (OK / Cancel), then stores it on its own, not with a prompt; one of 20 MB or more into separate storage, after asking. */
    void importPicked(File file, boolean result) {
        String name = file.getName();
        String kind = result ? "result file" : "reference file";
        String title = result ? "Import as Result file" : "Import as Ref file";
        if (file.length() >= PromptVault.LINK_MIN) {
            int ans = JOptionPane.showOptionDialog(app, DbFilter.linkQuestion(name, file.length()), title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new Object[] {"Import", "Cancel"}, "Import");
            if (ans != 0) {
                app.setNow("Not imported: " + name);
                return;
            }
            try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                vault().addLinkedFile(name, in, "Imported", result ? 3 : 1);
            } catch (Exception ex) {
                app.setNow("Could not import " + name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
                return;
            }
            this.imported(name, file.length(), result, true);
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (Exception ex) {
            app.setNow("Could not read " + name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            return;
        }
        int ans = JOptionPane.showOptionDialog(app, "Add " + name + " (" + Math.max(1, bytes.length / 1024) + " KB) to the prompt library as a " + kind + "?\n\n"
            + (result ? "It shows in Result files on the Prompts page." : "It shows in Ref files on the Prompts page and in Browse DB."),
            title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            new Object[] {"OK", "Cancel"}, "OK");
        if (ans != 0) return;
        try {
            vault().addLibraryFile(name, bytes, "Imported", result ? 3 : 1);
        } catch (Exception ex) {
            app.setNow("Could not import " + name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            return;
        }
        this.imported(name, bytes.length, result, false);
    }

    /** The gallery an import opened last, for the tests (0 Ref files, 1 Result files; -1 none). */
    static int lastImportGallery = -1;

    /**
     * Imported: said, which group it goes in (No group: none), then Ref files (or Result files)
     * opened on it (S for a sound or MIDI file).
     */
    void imported(String name, long size, boolean result, boolean linked) {
        app.setNow("Imported " + name + " as a " + (result ? "result file" : "reference file") + (linked ? " (in separate storage)" : ""));
        GroupMenus.afterAdd(app, name, size);
        String letter = DbFilter.afterImport(name);
        if (letter != null) galleryFilter = letter;
        lastImportGallery = result ? 1 : 0;
        String said = app.nowPlaying.getText();
        app.showView("prompts");
        app.setNow(said);
        SwingUtilities.invokeLater(() -> this.gallery(result));
    }
}
