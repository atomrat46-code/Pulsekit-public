package pulsekit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * The Media browser (MediaBrowser in PyJav's Java menu): a folder's pictures, videos and sounds as
 * preview thumbnails, its folders first. A picture opens full size (with zoom), a video plays
 * (in the window with VLC, else in the browser's player), a sound or a MIDI plays with Play / Stop;
 * a folder opens in its place, and Up goes back as far as the folder picked. As on the phone.
 */
final class MediaBrowser {
    private static final int CELL = 150;

    private final Pulsekit app;
    /** Thumbnails made this session, by path, size and time (a changed file gets a new one). */
    private final Map<String, ImageIcon> thumbs = new ConcurrentHashMap<String, ImageIcon>();

    /** The browser shown last, the folder in it, and its thumbnail reading, for the tests. */
    JDialog last;
    File shown;
    volatile Thread loading;

    MediaBrowser(Pulsekit app) {
        this.app = app;
    }

    /** The folder picked (Up stops there) of the folder shown. */
    File shownRoot;

    /** The Media browser again on `dir` under `root`, as Return from ImageUpscaler opens it. */
    void reopen(File root, File dir) {
        this.chooser = null;
        this.tab = "";
        this.other = null;
        loadLoop();
        this.show(root, dir);
    }

    /** Choose file: what to do with the file picked, or null for the Media browser itself. */
    java.util.function.Consumer<File> chooser;
    /** Choose file's tab: "recent", "download" or "fav:<n>". */
    String tab = "";
    /** For Choose file's "Other…": the system's file chooser. */
    Runnable other;

    /** Opens the Media browser on `dir` (the folder picked: Up stops there). */
    void open(File dir) {
        if (dir == null || !dir.isDirectory()) {
            app.setNow((dir == null ? "That" : dir.getPath()) + " is not a directory");
            return;
        }
        this.chooser = null;
        this.tab = "";
        this.other = null;
        this.show(dir);
    }

    /**
     * Choose file: the Media browser, with tabs for the Recent MB folder (the folder the Media browser
     * opened last), Download and the favourite folders, open on the tab used last; the file clicked
     * goes to `picked`. "Other…" opens the system's file chooser (`other`).
     */
    void choose(java.util.function.Consumer<File> picked, Runnable other) {
        loadLoop();
        this.chooser = picked;
        this.other = other;
        // The tab used last (a favourite, say), else the Recent MB folder.
        this.tab = MediaDir.openTab(MediaDir.lastRoot.length() > 0 && new File(MediaDir.lastRoot).isDirectory());
        File[] at = this.tabFolders(this.tab);
        this.show(at[0], at[1]);
    }

    /** The tab's folder: {root (Up stops there), the folder to show}. */
    File[] tabFolders(String t) {
        if (t.equals("recent") && new File(MediaDir.lastRoot).isDirectory()) {
            File root = new File(MediaDir.lastRoot).getAbsoluteFile();
            return new File[] {root, under(root, MediaDir.pathFor(root.getPath()))};
        }
        if (t.startsWith("fav:")) {
            int i = Integer.parseInt(t.substring(4));
            if (i < MediaDir.favourites.size()) {
                String[] part = MediaDir.favourites.get(i).split("\t");
                File root = new File(part[0]).getAbsoluteFile();
                if (root.isDirectory()) {
                    java.util.List<String> below = new ArrayList<String>();
                    for (int k = 1; k < part.length; k++) below.add(part[k]);
                    return new File[] {root, under(root, below)};
                }
            }
        }
        File dl = downloads();
        return new File[] {dl, dl};
    }

    /** The Download folder (~/Downloads, or ~/Download). */
    static File downloads() {
        File home = new File(System.getProperty("user.home", "."));
        File d = new File(home, "Downloads");
        if (!d.isDirectory() && new File(home, "Download").isDirectory()) d = new File(home, "Download");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** The deepest of the folders `below` (in order) still there under `root`. */
    private static File under(File root, java.util.List<String> below) {
        File start = root;
        for (String u : below) {
            File f = new File(u);
            if (!f.isDirectory() || !u.startsWith(start.getPath() + File.separator)) break;
            start = f;
        }
        return start;
    }

    private void show(File dir) {
        loadLoop();
        // The folder it was last in under this one, when it is still there.
        File root = dir.getAbsoluteFile();
        this.show(root, under(root, MediaDir.pathFor(root.getPath())));
    }

    private void show(File root, File start) {
        final JDialog dialog = new JDialog(app, "Media browser", true);
        dialog.setName("media-browser");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.last = dialog;
        this.fill(dialog, root, start);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
    }

    static List<MediaDir.Entry> list(File dir) {
        return list(dir, false);
    }

    /** As above; `every` (Choose file) lists every file. */
    static List<MediaDir.Entry> list(File dir, boolean every) {
        List<MediaDir.Entry> all = new ArrayList<MediaDir.Entry>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isHidden()) continue;
                MediaDir.Entry e = new MediaDir.Entry();
                e.name = f.getName();
                e.id = f.getAbsolutePath();
                e.folder = f.isDirectory();
                e.size = e.folder ? 0 : f.length();
                // When it was last changed (seconds): Sort by date.
                e.date = f.lastModified() / 1000;
                all.add(e);
            }
        }
        return MediaDir.shown(all, every);
    }

    /** The dialog's contents for `dir`; the thumbnails come in the background. */
    private void fill(final JDialog dialog, final File root, final File dir) {
        this.shown = dir;
        this.shownRoot = root;
        // Remembered: the folder picked and the folders opened under it, for the next time.
        final List<String> under = new ArrayList<String>();
        for (File f = dir; f != null && !f.equals(root); f = f.getParentFile()) under.add(0, f.getPath());
        // Choose file's Download tab does not change the Recent MB folder; a favourite opened does (it is the folder in use).
        if (this.chooser == null || "recent".equals(this.tab) || this.tab.startsWith("fav")) MediaDir.remember(root.getPath(), under);
        saveLoop();
        final AtomicBoolean gone = new AtomicBoolean();
        List<MediaDir.Entry> read = list(dir, this.chooser != null);
        // The library's prompt files (Prompt files), under T with a star.
        try {
            read = MediaDir.withPromptFiles(read, PromptDb.vault());
        } catch (Exception ex) {
            // No library: the folder only.
        }
        final List<MediaDir.Entry> allEntries = read;
        // The type selector (A I V S T O), then the tag filter: what of the folder is shown.
        MediaTags.load(PromptDb.dir());
        FileGroups.load(PromptDb.dir());
        FileFavourites.load(PromptDb.dir());
        FileFilter.use(FileFilter.MEDIA);
        MediaDir.filter = FileFilter.normal(MediaDir.filter);
        List<MediaDir.Entry> kept = FileFilter.entries(MediaTags.filtered(MediaDir.filtered(allEntries, MediaDir.filter), MediaTags.filter));
        // Search (⌕), then Sort by and Order.
        if (this.search.length() > 0 || !"A".equals(this.searchType)) kept = MediaDir.searched(kept, this.search, this.searchType);
        final List<MediaDir.Entry> entries = MediaDir.sortedBy(kept, MediaDir.sortBy, MediaDir.sortDesc);
        this.shownEntries = entries;
        this.listed.clear();
        for (MediaPlaylist.Item it : MediaPlaylist.items(PromptDb.dir(), dir.getAbsolutePath())) this.listed.add(it.id);
        dialog.setTitle("Media browser · " + MediaDir.label(dir.getPath()));
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new BorderLayout(8, 0));
        JLabel where = new JLabel("<html><b>" + esc(dir.getPath()) + "</b><br>" + MediaDir.summary(entries) + "</html>");
        where.setName("media-summary");
        top.add(where, BorderLayout.CENTER);
        javax.swing.JCheckBox loop = new javax.swing.JCheckBox("Loop videos", MediaDir.loopVideos);
        loop.setName("media-loop");
        loop.setToolTipText("A video starts again at its end");
        loop.addActionListener(e -> {
            MediaDir.loopVideos = loop.isSelected();
            saveLoop();
        });
        // Playlist (n) beside it, once this folder's playlist has a file.
        JButton playlist = new JButton("Playlist");
        playlist.setName("media-playlist");
        playlist.addActionListener(e -> this.playlist(dialog, dir));
        this.playlistButton = playlist;
        this.paintPlaylist();
        JPanel loopRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        loopRow.add(loop);
        loopRow.add(playlist);
        top.add(loopRow, BorderLayout.EAST);
        // Up stays at the folder Params gave too: there it goes on to the folder above (which becomes the top).
        final File above = dir.getParentFile();
        if (above != null && (!dir.equals(root) || (this.chooser == null && above.isDirectory() && above.canRead()))) {
            JButton up = new JButton("Up");
            up.setName("media-up");
            if (this.chooser == null) this.dropTarget(up, above, dialog, root, dir);
            up.addActionListener(e -> {
                gone.set(true);
                // The folder left shows again, outlined and scrolled to.
                this.focus = dir;
                this.fill(dialog, dir.equals(root) ? above : root, above);
            });
            top.add(up, BorderLayout.WEST);
        }
        // Add to favourites: this folder, as a tab in Choose file (★ In favourites when it is one).
        final boolean already = MediaDir.isFavourite(root.getPath(), under);
        JButton fav = new JButton(already ? MediaDir.STAR + "In favourites" : "Add to favourites");
        fav.setName("media-favourite");
        fav.addActionListener(e -> {
            boolean added = MediaDir.addFavourite(root.getPath(), under);
            saveLoop();
            String said = added ? MediaDir.label(dir.getPath()) + " added to favourites: a tab in Choose file" : MediaDir.label(dir.getPath()) + " is in favourites already";
            app.setNow(said);
            lastSaid = said;
            if (added) {
                gone.set(true);
                this.fill(dialog, root, dir);
            }
        });
        loopRow.add(fav, 0);
        // File: Import file…, Import folder…, Import files from zip…, Send folder contents to zip, Copy
        // folder from…, and Move defined media types.
        if (this.chooser == null) {
            final JButton fileButton = new JButton(FILE_MENU + " \u25be");
            fileButton.setName("media-file-menu");
            fileButton.addActionListener(e -> {
                javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
                String[][] items = {{this.selecting ? STOP_SELECTING : SELECT_MULTIPLE, "media-select-multiple"}, {IMPORT_FILE_ITEM, "media-import-file"}, {IMPORT_FOLDER_ITEM, "media-import-folder"}, {FolderZip.IMPORT, "media-import-zip"},
                    {FolderZip.SEND, "media-folder-zip"}, {COPY_FOLDER_ITEM, "media-copy-folder"}, {DownloadMove.DEFAULTS, "media-move-defaults"}, {Playlist.ADD_FOLDER, "media-add-folder-playlist"}};
                for (String[] it : items) {
                    javax.swing.JMenuItem mi = new javax.swing.JMenuItem(it[0]);
                    mi.setName(it[1]);
                    final String what = it[1];
                    mi.addActionListener(a -> this.fileItem(dialog, root, dir, what, gone));
                    m.add(mi);
                }
                MenuCancel.add(m);
                lastFileMenu = m;
                if (fileButton.isShowing()) m.show(fileButton, 0, fileButton.getHeight());
            });
            loopRow.add(fileButton, 0);
        }
        // The favourite folders, and the folders with a playlist: each opens here.
        if (this.chooser == null) {
            JButton favs = new JButton(MediaDir.STAR + "Favourites \u25be");
            favs.setName("media-favourites-list");
            favs.addActionListener(e -> this.favouritesList(favs, dialog));
            JButton lists = new JButton("\u2630 Playlists \u25be");
            lists.setName("media-playlists-list");
            lists.addActionListener(e -> this.playlistsList(lists, dialog));
            loopRow.add(favs, 0);
            loopRow.add(lists, 1);
        }
        // A all, I pictures, V videos, S sounds, T text, O other files.
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        final Runnable again = () -> {
            gone.set(true);
            this.fill(dialog, root, dir);
        };
        MediaDir.filter = AppSettings.letter(MediaDir.filter);
        for (int i = 0; i < MediaDir.FILTERS.length; i++) {
            final String f = MediaDir.FILTERS[i];
            // F G C come after; a type General settings leaves out has no letter.
            if (FileFilter.isToggle(f) || !AppSettings.shows(f)) continue;
            int n = MediaDir.count(allEntries, f);
            final javax.swing.JToggleButton b = new javax.swing.JToggleButton(f, f.equals(MediaDir.filter));
            b.setName("media-filter:" + f);
            b.setToolTipText(MediaDir.FILTER_NAMES[i] + " (" + n + ")");
            b.setEnabled(n > 0 || f.equals("A") || f.equals(MediaDir.filter));
            b.addActionListener(e -> {
                MediaDir.filter = f;
                again.run();
            });
            filters.add(b);
        }
        // F G C: switches with the letter (a right click: G the groups, C the categories).
        FilterBar.toggles(app, filters, "media-filter:", MediaDir.filter, again);
        filters.add(new JLabel(" " + MediaDir.FILTER_NAMES[java.util.Arrays.asList(MediaDir.FILTERS).indexOf(MediaDir.filter)]
            + FileFilter.title().replace(" \u00b7 Group: ", " \u00b7 group ").replace(" \u00b7 ", ", ")));
        // T1 T2 T3: the files with any of the tags picked (a row of their own).
        final JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        FilterBar.tags(tagRow, "media-filter:", again);
        // Tagged: only the files with a tag or a category (Tags… and Category… in a file's menu).
        final List<String[]> tagChoices = MediaTags.choices(allEntries);
        boolean known = false;
        for (String[] c : tagChoices) known |= c[1].equals(MediaTags.filter);
        if (!known) tagChoices.add(new String[] {MediaTags.filter + " (0)", MediaTags.filter});
        String[] tagNames = new String[tagChoices.size()];
        int at = 0;
        for (int i = 0; i < tagNames.length; i++) {
            tagNames[i] = tagChoices.get(i)[0];
            if (tagChoices.get(i)[1].equals(MediaTags.filter)) at = i;
        }
        final javax.swing.JComboBox<String> tagged = new javax.swing.JComboBox<String>(tagNames);
        tagged.setName("media-tag-filter");
        tagged.setSelectedIndex(at);
        tagged.setToolTipText("Only the files with this tag or category");
        tagged.addActionListener(e -> {
            int i = tagged.getSelectedIndex();
            if (i < 0 || tagChoices.get(i)[1].equals(MediaTags.filter)) return;
            MediaTags.filter = tagChoices.get(i)[1];
            gone.set(true);
            this.fill(dialog, root, dir);
        });
        filters.add(new JLabel("   Tagged:"));
        filters.add(tagged);
        // Sort by (name, date, type), Order, and ⌕ Search (a name with * and ?, and a type).
        final javax.swing.JComboBox<String> sortBy = new javax.swing.JComboBox<String>(MediaDir.SORTS);
        sortBy.setName("media-sort");
        sortBy.setSelectedItem(MediaDir.sortBy);
        sortBy.addActionListener(e -> {
            String picked = (String) sortBy.getSelectedItem();
            if (picked == null || picked.equals(MediaDir.sortBy)) return;
            MediaDir.sortBy = picked;
            // By date the newest first at first.
            MediaDir.sortDesc = MediaDir.SORT_DATE.equals(picked);
            saveLoop();
            gone.set(true);
            this.fill(dialog, root, dir);
        });
        filters.add(new JLabel("   Sort:"));
        filters.add(sortBy);
        JButton order = new JButton(MediaDir.orderLabel(MediaDir.sortBy, MediaDir.sortDesc));
        order.setName("media-order");
        order.addActionListener(e -> {
            MediaDir.sortDesc = !MediaDir.sortDesc;
            saveLoop();
            gone.set(true);
            this.fill(dialog, root, dir);
        });
        filters.add(order);
        final boolean searching = this.search.length() > 0 || !"A".equals(this.searchType);
        JButton find = new JButton(searching ? "\uD83D\uDD0D " + this.search + ("A".equals(this.searchType) ? "" : " (" + this.searchType + ")") : "\uD83D\uDD0D Search");
        find.setName("media-search");
        find.addActionListener(e -> {
            if (!this.searchDialog(dialog)) return;
            gone.set(true);
            this.fill(dialog, root, dir);
        });
        filters.add(find);
        if (searching) {
            JButton clear = new JButton("\u2715");
            clear.setName("media-search-clear");
            clear.addActionListener(e -> {
                this.search = "";
                this.searchType = "A";
                gone.set(true);
                this.fill(dialog, root, dir);
            });
            filters.add(clear);
        }
        // Select multiple: how many are ticked, All, None, Done.
        if (this.selecting) {
            JLabel count = new JLabel("   " + this.selected.size() + " selected (right click a ticked one: Remove, Move)");
            count.setName("media-selection-count");
            this.selectionCount = count;
            filters.add(count);
            JButton all = new JButton("All");
            all.setName("media-select-all");
            all.addActionListener(e -> {
                for (MediaDir.Entry en : entries) if (!en.folder && en.dbId == 0) this.selected.add(en.id);
                gone.set(true);
                this.fill(dialog, root, dir);
            });
            filters.add(all);
            JButton none = new JButton("None");
            none.setName("media-select-none");
            none.addActionListener(e -> {
                this.selected.clear();
                gone.set(true);
                this.fill(dialog, root, dir);
            });
            filters.add(none);
            JButton done = new JButton("Done");
            done.setName("media-select-done");
            done.addActionListener(e -> {
                this.selecting = false;
                this.selected.clear();
                gone.set(true);
                this.fill(dialog, root, dir);
            });
            filters.add(done);
        }
        JPanel north = new JPanel(new BorderLayout(0, 6));
        if (this.chooser != null) north.add(this.tabs(dialog, gone), BorderLayout.NORTH);
        north.add(top, BorderLayout.CENTER);
        JPanel rows = new JPanel(new GridLayout(2, 1, 0, 4));
        rows.add(filters);
        rows.add(tagRow);
        north.add(rows, BorderLayout.SOUTH);
        body.add(north, BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(0, 4, 8, 8));
        if (entries.isEmpty()) {
            // The F G C switches or the T lists may be what keeps the files out: say so.
            JLabel none = new JLabel(FileFilter.active() ? FileFilter.nothing() : "No pictures, videos or sounds here");
            none.setName("media-empty");
            grid.add(none);
        }
        final List<JButton> waiting = new ArrayList<JButton>();
        final List<File> videos = new ArrayList<File>();
        JButton focusCard = null;
        for (final MediaDir.Entry entry : entries) {
            final File f = new File(entry.id);
            JButton card = new JButton();
            card.setName((entry.folder ? "media-folder:" : "media-card:") + entry.name);
            card.setVerticalTextPosition(SwingConstants.BOTTOM);
            card.setHorizontalTextPosition(SwingConstants.CENTER);
            card.setPreferredSize(new Dimension(CELL + 20, CELL + 50));
            card.putClientProperty("media-file", f);
            ImageIcon icon = entry.folder ? null : this.thumbs.get(key(f));
            if (icon != null) card.setIcon(icon);
            card.setText(caption(entry, icon != null ? null : mark(entry)));
            if (entry.folder) {
                card.addActionListener(e -> {
                    gone.set(true);
                    this.fill(dialog, root, f);
                });
                // A right click: Rename, Delete. The first picture in it is its thumbnail.
                if (this.chooser == null) card.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        if (e.isPopupTrigger()) MediaBrowser.this.folderMenu(entry, f, card, e.getX(), e.getY(), dialog, root, dir);
                    }

                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        if (e.isPopupTrigger()) MediaBrowser.this.folderMenu(entry, f, card, e.getX(), e.getY(), dialog, root, dir);
                    }
                });
                // A file card dropped on it moves the file in.
                if (this.chooser == null) this.dropTarget(card, f, dialog, root, dir);
                File pic = firstPicture(f);
                if (pic != null) {
                    card.putClientProperty("media-folder-pic", pic);
                    ImageIcon have = this.thumbs.get(key(pic));
                    if (have != null) card.setIcon(have);
                    else waiting.add(card);
                }
                // Up from this folder: its card outlined and scrolled to.
                if (f.equals(this.focus)) {
                    card.setBorder(BorderFactory.createLineBorder(new java.awt.Color(0xE0B04A), 2));
                    focusCard = card;
                }
            } else {
                card.setToolTipText(entry.dbId != 0 ? "Shows its text (kept in the DB)" : entry.kind == MediaDir.PICTURE ? "Opens it full size" : "Plays it");
                // A right click, or holding the button down, opens its menu (a long press, as on the phone).
                final boolean[] held = new boolean[1];
                final boolean pickable = this.selecting && entry.dbId == 0;
                if (pickable) this.paintPicked(card, entry);
                card.addActionListener(e -> {
                    if (held[0]) held[0] = false;
                    else if (pickable) {
                        // Selecting: a click ticks it (or not).
                        if (!this.selected.remove(entry.id)) this.selected.add(entry.id);
                        this.paintPicked(card, entry);
                    } else if (entry.dbId != 0) {
                        // A library prompt file: Choose file gets a copy; else its text shows.
                        File copy = this.promptFile(entry);
                        if (copy == null) return;
                        if (this.chooser != null) {
                            java.util.function.Consumer<File> picked = this.chooser;
                            dialog.dispose();
                            picked.accept(copy);
                        } else {
                            this.openPromptFile(entry);
                        }
                    } else if (this.chooser != null) {
                        // Choose file: this file fills the row.
                        java.util.function.Consumer<File> picked = this.chooser;
                        dialog.dispose();
                        picked.accept(f);
                    } else this.openEntry(entry, f);
                });
                card.addMouseListener(new java.awt.event.MouseAdapter() {
                    javax.swing.Timer hold;

                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        if (entry.dbId != 0) return;
                        if (e.isPopupTrigger()) {
                            MediaBrowser.this.menu(entry, f, card, e.getX(), e.getY());
                            return;
                        }
                        if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) return;
                        final int x = e.getX();
                        final int y = e.getY();
                        this.hold = new javax.swing.Timer(600, ev -> {
                            held[0] = true;
                            MediaBrowser.this.menu(entry, f, card, x, y);
                        });
                        this.hold.setRepeats(false);
                        this.hold.start();
                    }

                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        if (this.hold != null) this.hold.stop();
                        if (entry.dbId == 0 && e.isPopupTrigger()) MediaBrowser.this.menu(entry, f, card, e.getX(), e.getY());
                        // A library prompt file (★): Rename (its name in the DB).
                        else if (entry.dbId != 0 && e.isPopupTrigger() && MediaBrowser.this.chooser == null) MediaBrowser.this.dbMenu(entry, card, e.getX(), e.getY());
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        if (this.hold != null) this.hold.stop();
                    }
                });
                // Dragged onto a folder card (or Up): moved there.
                if (this.chooser == null && entry.dbId == 0) this.dragSource(card, f);
                if (icon == null && (entry.kind == MediaDir.PICTURE || entry.kind == MediaDir.VIDEO)) waiting.add(card);
                if (icon == null && entry.kind == MediaDir.VIDEO) videos.add(f);
            }
            grid.add(card);
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(grid, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrap);
        scroll.setPreferredSize(new Dimension(4 * (CELL + 28) + 24, 520));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        // Where this folder was last scrolled to comes back; moving remembers it (kept on the next save).
        final String placeOf = dir.getAbsolutePath();
        final int was = MediaDir.place(placeOf);
        final JButton focused = focusCard;
        this.focus = null;
        if (focused != null) SwingUtilities.invokeLater(() -> focused.scrollRectToVisible(new java.awt.Rectangle(0, 0, focused.getWidth(), focused.getHeight())));
        else if (was > 0) SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(was));
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting() || e.getValue() > 0) MediaDir.place(placeOf, e.getValue());
        });
        body.add(scroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        // Without VLC, the browser reads the videos' first frames (one tab for them all).
        if (!videos.isEmpty() && !VlcPlayer.available()) {
            JButton make = new JButton("Make video previews (" + videos.size() + ")");
            make.setName("media-make-previews");
            make.setToolTipText("Your browser reads each video's first frame. With VLC installed they are made here.");
            make.addActionListener(e -> this.browserPreviews(videos, dialog, root, dir));
            bottom.add(make);
        }
        JButton close = new JButton("Close");
        close.setName("media-close");
        close.addActionListener(e -> dialog.dispose());
        bottom.add(close);
        body.add(bottom, BorderLayout.SOUTH);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                gone.set(true);
                saveLoop();
            }
        });
        dialog.setContentPane(body);
        dialog.revalidate();
        dialog.repaint();
        this.load(waiting, gone);
    }

    /** Choose file's tabs: Recent MB folder, Download, the favourites (a right click takes one out), Other…. */
    private JPanel tabs(final JDialog dialog, final AtomicBoolean gone) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        java.util.List<String[]> all = new ArrayList<String[]>();
        if (MediaDir.lastRoot.length() > 0 && new File(MediaDir.lastRoot).isDirectory()) all.add(new String[] {"recent", "Recent MB folder"});
        all.add(new String[] {"download", "Download"});
        for (int i = 0; i < MediaDir.favourites.size(); i++) all.add(new String[] {"fav:" + i, MediaDir.favouriteLabel(MediaDir.favourites.get(i))});
        for (final String[] t : all) {
            javax.swing.JToggleButton b = new javax.swing.JToggleButton(t[1], t[0].equals(this.tab));
            b.setName("media-tab:" + t[0]);
            b.addActionListener(e -> {
                if (t[0].equals(this.tab)) {
                    b.setSelected(true);
                    return;
                }
                this.tab = t[0];
                // Choose file opens on this tab next time.
                MediaDir.usedTab(t[0]);
                saveLoop();
                File[] at = this.tabFolders(t[0]);
                gone.set(true);
                this.fill(dialog, at[0], at[1]);
            });
            if (t[0].startsWith("fav:")) {
                b.setToolTipText("A right click takes it out of the favourites");
                b.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        if (!e.isPopupTrigger() && !javax.swing.SwingUtilities.isRightMouseButton(e)) return;
                        MediaDir.removeFavourite(Integer.parseInt(t[0].substring(4)));
                        saveLoop();
                        app.setNow(t[1] + " is no longer a favourite");
                        MediaBrowser.this.tab = "download";
                        File[] at = MediaBrowser.this.tabFolders("download");
                        gone.set(true);
                        MediaBrowser.this.fill(dialog, at[0], at[1]);
                    }
                });
            }
            row.add(b);
        }
        if (this.other != null) {
            JButton o = new JButton("Other\u2026");
            o.setName("media-tab:other");
            o.addActionListener(e -> {
                Runnable r = this.other;
                dialog.dispose();
                r.run();
            });
            row.add(o);
        }
        return row;
    }

    /** Reads the pictures' thumbnails, and with VLC the videos' first frames, putting each on its card as it comes. */
    private void load(final List<JButton> cards, final AtomicBoolean gone) {
        if (cards.isEmpty()) return;
        Thread t = new Thread(() -> {
            boolean vlc = VlcPlayer.available();
            for (final JButton card : cards) {
                if (gone.get()) break;
                File f = (File) card.getClientProperty("media-file");
                // A folder card: the first picture in it.
                if (card.getClientProperty("media-folder-pic") != null) f = (File) card.getClientProperty("media-folder-pic");
                int kind = MediaDir.kind(f.getName());
                ImageIcon icon = null;
                try {
                    if (kind == MediaDir.PICTURE) {
                        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
                        if (img != null) this.sizes.put(f.getAbsolutePath(), MediaSize.label(img.getWidth(), img.getHeight()));
                        icon = scaled(img);
                    } else if (kind == MediaDir.VIDEO) {
                        // Its size from the MP4's own headers (no VLC needed).
                        long[] head = MediaDir.mp4Info(f);
                        if (head != null) this.sizes.put(f.getAbsolutePath(), MediaSize.label((int) head[1], (int) head[2]));
                        if (vlc) {
                            byte[] jpeg = VlcPlayer.firstFrame(f, 320);
                            if (jpeg != null) icon = scaled(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpeg)));
                        }
                    }
                } catch (Throwable ex) {
                    icon = null;
                }
                if (icon == null) {
                    if (this.sizes.containsKey(f.getAbsolutePath())) SwingUtilities.invokeLater(() -> this.put(card, null));
                    continue;
                }
                this.thumbs.put(key(f), icon);
                final ImageIcon shownIcon = icon;
                SwingUtilities.invokeLater(() -> this.put(card, shownIcon));
            }
            this.loading = null;
        }, "pulsekit-media-thumbs");
        t.setDaemon(true);
        this.loading = t;
        t.start();
    }

    /** Each picture's and video's "1920×1080 · 16:9", by path, as its thumbnail was read. */
    private final Map<String, String> sizes = new ConcurrentHashMap<String, String>();

    private void put(JButton card, ImageIcon icon) {
        File f = (File) card.getClientProperty("media-file");
        MediaDir.Entry e = new MediaDir.Entry();
        e.name = f.getName();
        e.id = f.getAbsolutePath();
        e.folder = f.isDirectory();
        e.size = e.folder ? 0 : f.length();
        e.kind = e.folder ? 0 : MediaDir.kind(e.name);
        if (icon != null) card.setIcon(icon);
        card.setText(caption(e, card.getIcon() != null ? null : mark(e)));
    }

    /** Video previews by the browser (no VLC): one tab reads the first frames, and the folder is shown again with them. */
    private void browserPreviews(final List<File> videos, final JDialog dialog, final File root, final File dir) {
        try {
            FrameGrab grab = new FrameGrab(videos, made -> {
                int kept = 0;
                for (Map.Entry<Integer, byte[]> e : made.entrySet()) {
                    try {
                        ImageIcon icon = scaled(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(e.getValue())));
                        if (icon == null) continue;
                        this.thumbs.put(key(videos.get(e.getKey())), icon);
                        kept++;
                    } catch (Exception ex) {
                        // that one keeps its type
                    }
                }
                app.setNow("Made " + kept + " of " + videos.size() + (videos.size() == 1 ? " video preview" : " video previews"));
                if (kept > 0 && dialog.isDisplayable() && dir.equals(this.shown)) this.fill(dialog, root, dir);
            });
            PromptDb.browser.accept(grab.start());
            app.setNow("Making video previews in the browser…");
        } catch (Exception ex) {
            app.setNow("Could not make video previews: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
        }
    }

    JButton playlistButton;

    /** The Playlist (n) button: shown once the shown folder's playlist has a file. */
    void paintPlaylist() {
        if (this.playlistButton == null || this.shown == null) return;
        int n = MediaPlaylist.items(PromptDb.dir(), this.shown.getAbsolutePath()).size();
        this.playlistButton.setText(MediaPlaylist.button(n));
        this.playlistButton.setVisible(n > 0);
        java.awt.Container parent = this.playlistButton.getParent();
        if (parent != null) parent.revalidate();
    }

    /** The thumbnail shown while a playlist item is held, for the tests. */
    javax.swing.JWindow lastPeek;
    JLabel lastPeekImage;
    JLabel lastPeekInfo;

    /** A playlist item's thumbnail beside the list, while it is held (a sound shows its type), with a video's length and resolution, a picture's size. */
    void peek(java.awt.Component near, final MediaDir.Entry e, final File f) {
        this.unpeek();
        final int side = 320;
        java.awt.Window owner = SwingUtilities.getWindowAncestor(near);
        final javax.swing.JWindow w = new javax.swing.JWindow(owner);
        w.setName("playlist-peek");
        final JLabel image = new JLabel("<html><center><div style='font-size:18px;color:#8A8B86'>" + mark(e) + "</div>" + esc(e.name) + "</center></html>", SwingConstants.CENTER);
        image.setName("playlist-peek-image");
        image.setOpaque(true);
        image.setBackground(java.awt.Color.BLACK);
        image.setForeground(java.awt.Color.WHITE);
        image.setPreferredSize(new Dimension(side, side));
        final JLabel info = new JLabel(" ", SwingConstants.CENTER);
        info.setName("playlist-peek-info");
        info.setOpaque(true);
        info.setBackground(java.awt.Color.BLACK);
        info.setForeground(java.awt.Color.WHITE);
        info.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(java.awt.Color.BLACK);
        box.add(image, BorderLayout.CENTER);
        box.add(info, BorderLayout.SOUTH);
        w.setContentPane(box);
        w.pack();
        java.awt.Point at = near.getLocationOnScreen();
        w.setLocation(at.x + near.getWidth() + 8, Math.max(0, at.y - side / 2));
        this.lastPeek = w;
        this.lastPeekImage = image;
        this.lastPeekInfo = info;
        w.setVisible(true);
        if (e.kind != MediaDir.PICTURE && e.kind != MediaDir.VIDEO) return;
        final ImageIcon have = e.kind == MediaDir.PICTURE ? null : this.thumbs.get(key(f));
        if (have != null) this.showPeek(w, image, have.getImage(), side);
        Thread t = new Thread(() -> {
            try {
                java.awt.image.BufferedImage img = null;
                String line = "";
                if (e.kind == MediaDir.PICTURE) {
                    img = javax.imageio.ImageIO.read(f);
                    if (img != null) line = MediaDir.info(MediaDir.PICTURE, img.getWidth(), img.getHeight(), 0, f.length());
                } else {
                    // VLC reads its first frame, size and length; else the MP4's own headers tell the last two.
                    VlcPlayer.Probe probe = VlcPlayer.available() ? VlcPlayer.probe(f, side) : null;
                    if (probe != null && have == null) img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(probe.jpeg));
                    long[] head = MediaDir.mp4Info(f);
                    long len = probe != null && probe.lengthMs > 0 ? probe.lengthMs : head != null ? head[0] : 0;
                    int vw = probe != null && probe.width > 0 ? probe.width : head != null ? (int) head[1] : 0;
                    int vh = probe != null && probe.height > 0 ? probe.height : head != null ? (int) head[2] : 0;
                    line = MediaDir.info(MediaDir.VIDEO, vw, vh, len, f.length());
                }
                final java.awt.image.BufferedImage got = img;
                final String shown = line;
                SwingUtilities.invokeLater(() -> {
                    if (this.lastPeek != w) return;
                    if (got != null) this.showPeek(w, image, got, side);
                    if (shown.length() > 0) info.setText(shown);
                });
            } catch (Exception ex) {
                // it keeps its type
            }
        }, "pulsekit-playlist-peek");
        t.setDaemon(true);
        t.start();
    }

    private void showPeek(javax.swing.JWindow w, JLabel image, Image img, int side) {
        if (this.lastPeek != w || !w.isVisible()) return;
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) return;
        double s = Math.min(side / (double) iw, side / (double) ih);
        image.setText(null);
        image.setIcon(new ImageIcon(img.getScaledInstance(Math.max(1, (int) (iw * s)), Math.max(1, (int) (ih * s)), Image.SCALE_SMOOTH)));
    }

    void unpeek() {
        if (this.lastPeek != null) this.lastPeek.dispose();
    }

    /** The playlist window shown last, for the tests. */
    JDialog lastPlaylist;

    /** The folder's default playlist: its files, in order; a click plays or opens one as its thumbnail does. */
    void playlist(JDialog owner, File folder) {
        final JDialog d = new JDialog(owner, "Playlist \u00b7 " + MediaDir.label(folder.getPath()), true);
        d.setName("media-playlist-window");
        JPanel list = new JPanel(new GridLayout(0, 1, 0, 4));
        int i = 0;
        for (MediaPlaylist.Item it : MediaPlaylist.items(PromptDb.dir(), folder.getAbsolutePath())) {
            final MediaDir.Entry e = new MediaDir.Entry();
            e.id = it.id;
            e.name = it.name;
            e.size = it.size;
            e.kind = MediaDir.kind(it.name);
            final File f = new File(it.id);
            String what = e.kind == MediaDir.PICTURE ? "picture" : e.kind == MediaDir.VIDEO ? "video" : "sound";
            JButton row = new JButton((++i) + ".  " + it.name + "   \u00b7 " + what);
            row.setName("playlist-item:" + it.name);
            row.setHorizontalAlignment(SwingConstants.LEFT);
            // Holding the button down shows its thumbnail; letting go closes it (and opens nothing).
            final boolean[] held = new boolean[1];
            row.addActionListener(ev -> {
                if (held[0]) held[0] = false;
                else if (!f.isFile()) app.setNow(it.name + " is no longer there");
                else this.openEntry(e, f);
            });
            row.addMouseListener(new java.awt.event.MouseAdapter() {
                javax.swing.Timer hold;

                @Override
                public void mousePressed(java.awt.event.MouseEvent ev) {
                    if (!SwingUtilities.isLeftMouseButton(ev)) return;
                    this.hold = new javax.swing.Timer(500, t -> {
                        held[0] = true;
                        MediaBrowser.this.peek(row, e, f);
                    });
                    this.hold.setRepeats(false);
                    this.hold.start();
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent ev) {
                    if (this.hold != null) this.hold.stop();
                    MediaBrowser.this.unpeek();
                }
            });
            list.add(row);
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrap);
        scroll.setPreferredSize(new Dimension(460, 320));
        JButton close = new JButton("Close");
        close.addActionListener(ev -> d.dispose());
        // Save pls: this folder's playlist kept as a .pls with the playlists (File > Playlist viewer/editor).
        JButton savePls = new JButton("Save pls");
        savePls.setName("media-playlist-save-pls");
        savePls.addActionListener(ev -> {
            Object typed = answer != null ? answer.apply("Playlist name") : javax.swing.JOptionPane.showInputDialog(d, "Playlist name", "Save pls", javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, folder.getName());
            String name = typed == null ? "" : Playlist.clean(typed.toString());
            if (name.length() == 0) return;
            app.setNow(this.savePls(folder, name));
        });
        JButton openList = new JButton("Open in Playlist viewer");
        openList.setName("media-playlist-open-viewer");
        openList.addActionListener(ev -> {
            String said = this.savePls(folder, Playlist.clean(folder.getName()));
            app.setNow(said);
            d.dispose();
            PlaylistWindow.open(app, Playlist.clean(folder.getName()));
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        bottom.add(savePls);
        bottom.add(openList);
        bottom.add(close);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        body.add(scroll, BorderLayout.CENTER);
        body.add(bottom, BorderLayout.SOUTH);
        d.setContentPane(body);
        d.pack();
        d.setLocationRelativeTo(owner);
        this.lastPlaylist = d;
        d.setVisible(true);
    }

    /** The card menu shown last, for the tests. */
    javax.swing.JPopupMenu lastMenu;

    /** Save pls: the folder's playlist as the named .pls with the playlists (replacing one of that name); the status line. */
    /** What the last import said (for the tests). */
    static String lastImport;

    /**
     * The folder zips' questions answered (for the tests): the question ("zip file", "password",
     * "where", or "shown <password>") to the answer; null: asked.
     */
    static java.util.function.Function<String, String> zipAnswer;

    /** Send folder contents to zip: the folder's files into "<folder>.zip" there, with a password made and shown once; what it did. */
    static final String FILE_MENU = "File";
    static final String IMPORT_FILE_ITEM = "Import file\u2026", IMPORT_FOLDER_ITEM = "Import folder\u2026", COPY_FOLDER_ITEM = "Copy folder from\u2026";

    /** Move defined media types' window shown last (for the tests). */
    static JDialog lastMoveWindow;

    /** The File menu shown last (for the tests). */
    static javax.swing.JPopupMenu lastFileMenu;

    /** A File menu item: its name ("media-import-file", ...). */
    void fileItem(JDialog dialog, File root, File dir, String what, AtomicBoolean gone) {
        if ("media-add-folder-playlist".equals(what)) {
            this.addFolderToPlaylist(dialog, dir);
            return;
        }
        if ("media-select-multiple".equals(what)) {
            this.selecting = !this.selecting;
            this.selected.clear();
            gone.set(true);
            this.fill(dialog, root, dir);
            return;
        }
        if ("media-move-defaults".equals(what)) {
            this.moveDefined(dialog, root, dir, gone);
            return;
        }
        String said = "media-folder-zip".equals(what) ? this.folderToZip(dialog, dir) : "media-import-zip".equals(what) ? this.importZip(dialog, dir) : this.importHere(dialog, dir, what);
        if (said == null) return;
        app.setNow(said);
        lastImport = said;
        gone.set(true);
        this.fill(dialog, root, dir);
    }

    /** Move defined media types: with no type ticked with a folder, the folders asked for first; then moved in the background. */
    void moveDefined(final JDialog dialog, final File root, final File dir, final AtomicBoolean gone) {
        SogniDownloads.init(PromptDb.dir());
        DownloadMove.load();
        if (!DownloadMove.anyDefault()) {
            JPanel ask = new JPanel();
            ask.setLayout(new javax.swing.BoxLayout(ask, javax.swing.BoxLayout.Y_AXIS));
            JLabel text = new JLabel(DownloadMove.FOLDERS_ASK);
            text.setAlignmentX(0.0f);
            ask.add(text);
            DownloadsScreen.folderRows(dialog, ask);
            Object[] options = {"Move", "Cancel"};
            if (javax.swing.JOptionPane.showOptionDialog(dialog, ask, DownloadMove.SETTINGS_TITLE, javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[0]) != 0) return;
            if (!DownloadMove.anyDefault()) {
                app.setNow("Not moved: no type is ticked with a folder");
                return;
            }
        }
        app.setNow("Moving the defined media types\u2026");
        // What is moving: the file, how many of how many, and a bar (Hide: it goes on).
        final JDialog progress = new JDialog(dialog, DownloadMove.DEFAULTS, false);
        progress.setName("media-move-window");
        JPanel box = new JPanel(new java.awt.BorderLayout(0, 8));
        box.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 14, 12, 14));
        final JLabel status = new JLabel("Looking for files\u2026");
        status.setName("media-move-status");
        final javax.swing.JProgressBar bar = new javax.swing.JProgressBar(0, 1000);
        bar.setName("media-move-progress");
        bar.setPreferredSize(new java.awt.Dimension(420, bar.getPreferredSize().height));
        final JButton hide = new JButton("Hide");
        hide.setName("media-move-hide");
        hide.addActionListener(e -> progress.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.add(hide);
        box.add(status, java.awt.BorderLayout.NORTH);
        box.add(bar, java.awt.BorderLayout.CENTER);
        box.add(south, java.awt.BorderLayout.SOUTH);
        progress.setContentPane(box);
        progress.pack();
        progress.setLocationRelativeTo(dialog);
        progress.setVisible(true);
        lastMoveWindow = progress;
        final javax.swing.Timer watch = new javax.swing.Timer(200, e -> {
            if (DownloadsScreen.moveStatus.length() > 0) status.setText(DownloadsScreen.moveStatus);
            bar.setValue(DownloadsScreen.moveProgress);
        });
        watch.start();
        new Thread(() -> {
            final String said = DownloadsScreen.moveToDefaults(dir);
            lastSaid = said;
            SwingUtilities.invokeLater(() -> {
                watch.stop();
                status.setText(said);
                bar.setValue(1000);
                hide.setText("Close");
                app.setNow(said);
                gone.set(true);
                this.fill(dialog, root, dir);
            });
        }, "move-defaults").start();
    }

    String folderToZip(JDialog dialog, File dir) {
        String name = FolderZip.zipFor(dir.getName());
        File out = new File(dir, name);
        for (int n = 1; out.exists(); n++) out = new File(dir, name.replaceAll("\\.zip$", "") + " (" + n + ").zip");
        List<ZipPack.Source> sources = FolderZip.files(dir, out.getName());
        if (sources.isEmpty()) return dir.getName() + " has no files to zip";
        String pw = FolderZip.password();
        try {
            ZipPack.write(out, sources, pw.toCharArray());
        } catch (Exception ex) {
            out.delete();
            return "Could not zip " + dir.getName() + ": " + ex.getMessage();
        }
        String said = FolderZip.sent(out.getName(), sources.size(), dir.getName());
        if (zipAnswer != null) {
            zipAnswer.apply("shown " + pw);
        } else {
            // The password, to copy: Pulsekit does not keep it.
            javax.swing.JTextField field = new javax.swing.JTextField(pw, 22);
            field.setEditable(false);
            field.setName("media-zip-password");
            field.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 15));
            JPanel box = new JPanel(new BorderLayout(0, 6));
            box.add(new JLabel(said + "."), BorderLayout.NORTH);
            box.add(field, BorderLayout.CENTER);
            box.add(new JLabel(FolderZip.KEEP), BorderLayout.SOUTH);
            Object[] options = {"Copy password", "Close"};
            int ans = javax.swing.JOptionPane.showOptionDialog(dialog, box, "Password for " + out.getName(), javax.swing.JOptionPane.DEFAULT_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (ans == 0) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(pw), null);
        }
        return said;
    }

    /** Import files from zip: a zip picked, its password, then into this folder or a folder named for the zip; what it did, or null. */
    String importZip(JDialog dialog, File dir) {
        File zip;
        if (zipAnswer != null) {
            String a = zipAnswer.apply("zip file");
            if (a == null) return null;
            zip = new File(a);
        } else {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
            chooser.setDialogTitle(FolderZip.IMPORT);
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Zip files", "zip"));
            if (chooser.showOpenDialog(dialog) != javax.swing.JFileChooser.APPROVE_OPTION) return null;
            zip = chooser.getSelectedFile();
        }
        if (zip == null || !zip.isFile()) return null;
        List<ZipPack.Entry> entries;
        try {
            entries = ZipPack.list(zip);
        } catch (Exception ex) {
            return "Could not read " + zip.getName() + ": " + ex.getMessage();
        }
        char[] pw = null;
        while (ZipPack.encrypted(entries)) {
            String typed;
            if (zipAnswer != null) typed = zipAnswer.apply("password");
            else {
                javax.swing.JPasswordField field = new javax.swing.JPasswordField(22);
                int ans = javax.swing.JOptionPane.showConfirmDialog(dialog, new Object[] {ZipPack.ASK_PASSWORD, field}, zip.getName(), javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    javax.swing.JOptionPane.PLAIN_MESSAGE);
                typed = ans == javax.swing.JOptionPane.OK_OPTION ? new String(field.getPassword()) : null;
            }
            if (typed == null) return "Not imported: " + zip.getName();
            if (ZipPack.passwordOk(zip, entries, typed.toCharArray())) {
                pw = typed.toCharArray();
                break;
            }
            app.setNow(ZipPack.WRONG_PASSWORD + " " + zip.getName());
            if (zipAnswer != null) return ZipPack.WRONG_PASSWORD + " " + zip.getName();
        }
        String folder = FolderZip.folderFor(zip.getName());
        File sub = new File(dir, folder);
        String[] where = {FolderZip.HERE, FolderZip.newFolder(folder, sub.isDirectory())};
        String picked = zipAnswer != null ? zipAnswer.apply("where")
            : (String) javax.swing.JOptionPane.showInputDialog(dialog, ZipImport.extractQuestion(zip.getName(), entries), FolderZip.WHERE_TITLE,
                javax.swing.JOptionPane.QUESTION_MESSAGE, null, where, where[0]);
        if (picked == null) return "Not imported: " + zip.getName();
        File into = picked.equals(FolderZip.HERE) ? dir : sub;
        try {
            int[] done = FolderZip.extractInto(zip, entries, pw, into);
            return FolderZip.imported(zip.getName(), done, into.getName());
        } catch (Exception ex) {
            return "Could not import " + zip.getName() + ": " + ex.getMessage();
        }
    }

    /** Files (Import file…), a folder's files (Import folder…) or a whole folder (Copy folder from…) copied into `dir`; what it did, or null. */
    String importHere(JDialog dialog, File dir, String what) {
        boolean files = what.equals("media-import-file");
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle(files ? "Import file" : what.equals("media-import-folder") ? "Import folder (its files)" : "Copy folder from");
        chooser.setFileSelectionMode(files ? javax.swing.JFileChooser.FILES_ONLY : javax.swing.JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(files);
        if (chooser.showOpenDialog(dialog) != javax.swing.JFileChooser.APPROVE_OPTION) return null;
        List<File> from = new ArrayList<File>();
        if (files) {
            File[] picked = chooser.getSelectedFiles();
            if (picked == null || picked.length == 0) picked = new File[] {chooser.getSelectedFile()};
            for (File f : picked) if (f != null) from.add(f);
        } else {
            File folder = chooser.getSelectedFile();
            if (folder == null || !folder.isDirectory()) return "Not a folder";
            if (what.equals("media-copy-folder")) from.add(folder);
            else {
                File[] kids = folder.listFiles();
                if (kids != null) for (File k : kids) if (k.isFile() && !k.getName().startsWith(".")) from.add(k);
            }
        }
        int done = 0, failed = 0;
        for (File f : from) {
            if (dir.getAbsolutePath().startsWith(f.getAbsolutePath() + File.separator) || dir.equals(f)) {
                failed++;
                continue;
            }
            try {
                MediaDir.copyInto(f, dir);
                done++;
            } catch (Exception ex) {
                failed++;
            }
        }
        String kind = what.equals("media-copy-folder") ? (done == 1 ? " folder" : " folders") : (done == 1 ? " file" : " files");
        return "Copied " + done + kind + " into " + dir.getName() + (failed > 0 ? " (" + failed + " could not be copied)" : "");
    }

    /** What the favourites button said last, and the lists shown last (for the tests). */
    static String lastSaid;
    static JPopupMenu lastFavourites, lastPlaylists;

    /** ★ Favourites: the favourite folders, each with Open and Its favourite files (F on). */
    void favouritesList(java.awt.Component at, JDialog dialog) {
        JPopupMenu m = new JPopupMenu();
        if (MediaDir.favourites.isEmpty()) {
            JMenuItem none = new JMenuItem("No favourite folders yet: Add to favourites in a folder");
            none.setEnabled(false);
            m.add(none);
        }
        for (final String fav : new java.util.ArrayList<String>(MediaDir.favourites)) {
            javax.swing.JMenu one = new javax.swing.JMenu(MediaDir.favouriteLabel(fav));
            one.setName("media-fav-folder:" + MediaDir.favouriteLabel(fav));
            JMenuItem open = new JMenuItem("Open");
            open.setName("media-fav-open");
            open.addActionListener(e -> this.openFavourite(fav, false, dialog));
            JMenuItem only = new JMenuItem(MediaDir.STAR + "Its favourite files");
            only.setName("media-fav-files");
            only.addActionListener(e -> this.openFavourite(fav, true, dialog));
            one.add(open);
            one.add(only);
            m.add(one);
        }
        MenuCancel.add(m);
        lastFavourites = m;
        if (at.isShowing()) m.show(at, 0, at.getHeight());
    }

    /** A favourite folder opened here; with `onlyFavourites`, F on: its favourite files. */
    void openFavourite(String fav, boolean onlyFavourites, JDialog dialog) {
        String[] part = fav.split("\t");
        File root = new File(part[0]);
        File dir = root;
        for (int k = 1; k < part.length; k++) {
            File next = new File(part[k]);
            if (next.isDirectory()) dir = next;
        }
        if (!dir.isDirectory()) {
            app.setNow(MediaDir.favouriteLabel(fav) + " is not there any more");
            return;
        }
        FileFilter.use(FileFilter.MEDIA);
        if (onlyFavourites) FileFilter.fav = true;
        this.fill(dialog, root, dir);
        if (onlyFavourites) app.setNow(MediaDir.STAR + "The favourite files in " + MediaDir.favouriteLabel(fav) + " (F off: all of them)");
    }

    /** ☰ Playlists: the folders with files in their default playlist, each with Play (its playlist here), Open (the Playlist window) and Save as…. */
    void playlistsList(java.awt.Component at, JDialog dialog) {
        JPopupMenu m = new JPopupMenu();
        java.util.List<String[]> folders = MediaPlaylist.folders(PromptDb.dir());
        if (folders.isEmpty()) {
            JMenuItem none = new JMenuItem("No folder has a playlist yet: Add to default playlist on a file");
            none.setEnabled(false);
            m.add(none);
        }
        for (final String[] f : folders) {
            final File folder = new File(f[0]);
            final String name = Playlist.clean(MediaPlaylist.folderLabel(f[0]));
            javax.swing.JMenu one = new javax.swing.JMenu(MediaPlaylist.folderLabel(f[0]) + " (" + f[1] + ")");
            one.setName("media-pl-folder:" + MediaPlaylist.folderLabel(f[0]));
            JMenuItem play = new JMenuItem("Play here");
            play.setName("media-pl-play");
            play.addActionListener(e -> {
                if (folder.isDirectory()) this.fill(dialog, folder, folder);
                this.playlist(dialog, folder);
            });
            JMenuItem open = new JMenuItem("Open");
            open.setName("media-pl-open");
            open.addActionListener(e -> {
                app.setNow(this.savePls(folder, name));
                PlaylistWindow.open(app, name);
            });
            JMenuItem saveAs = new JMenuItem("Save as\u2026");
            saveAs.setName("media-pl-saveas");
            saveAs.addActionListener(e -> {
                app.setNow(this.savePls(folder, name));
                PlaylistWindow.saveAs(app, dialog, Playlist.load(PromptDb.dir(), name));
            });
            one.add(play);
            one.add(open);
            one.add(saveAs);
            m.add(one);
        }
        MenuCancel.add(m);
        lastPlaylists = m;
        if (at.isShowing()) m.show(at, 0, at.getHeight());
    }

    String savePls(File folder, String name) {
        Playlist p = new Playlist(name);
        for (MediaPlaylist.Item it : MediaPlaylist.items(PromptDb.dir(), folder.getAbsolutePath())) p.add(it.id, it.name);
        try {
            Playlist.save(PromptDb.dir(), p);
            return "Saved " + p.name + Playlist.EXT + " (" + p.items.size() + (p.items.size() == 1 ? " file)" : " files)") + ": File > Playlist viewer/editor";
        } catch (Exception ex) {
            return "Could not save " + p.name + Playlist.EXT + ": " + ex.getMessage();
        }
    }

    /** A file's menu (MediaDir.MENU): Add to DB as a reference or result file, Add to default playlist. */
    /** Search (⌕): a name (* any letters, ? one), and a type. */
    String search = "";
    String searchType = "A";
    /** Select multiple: on, and the files ticked (their paths). */
    boolean selecting;
    final java.util.Set<String> selected = new java.util.LinkedHashSet<String>();
    JLabel selectionCount;
    List<MediaDir.Entry> shownEntries = new ArrayList<MediaDir.Entry>();
    /** The tests: Search's answer {pattern, type} (null: ask). */
    static String[] searchAnswer;

    static final String SELECT_MULTIPLE = "Select multiple", STOP_SELECTING = "Stop selecting";
    static final String REMOVE_SELECTED = "Remove selected", MOVE_SELECTED = "Move selected to\u2026";

    /** The tests: Add folder's media files to playlist's answer {pictures, videos, music ("1"/"0"), playlist (Playlist.ADD_NEW: new), new name}. */
    static String[] addFolderAnswer;
    static String lastAddFolderSaid;

    /**
     * Add folder's media files to playlist: Images, Videos and Music to tick, then the playlist (Add new
     * first: its name asked); the folder's files of the kinds ticked go into it.
     */
    void addFolderToPlaylist(java.awt.Component at, File dir) {
        List<MediaDir.Entry> all = list(dir, false);
        int[] counts = new int[3];
        for (MediaDir.Entry e : all) {
            if (e.folder) continue;
            int k = MediaDir.kind(e.name);
            if (k == MediaDir.PICTURE) counts[0]++;
            else if (k == MediaDir.VIDEO) counts[1]++;
            else if (k == MediaDir.SOUND) counts[2]++;
        }
        List<String> names = new ArrayList<String>();
        names.add(Playlist.ADD_NEW);
        names.addAll(Playlist.names(PromptDb.dir()));
        boolean pics, vids, music;
        String playlist;
        if (addFolderAnswer != null) {
            pics = "1".equals(addFolderAnswer[0]);
            vids = "1".equals(addFolderAnswer[1]);
            music = "1".equals(addFolderAnswer[2]);
            playlist = Playlist.ADD_NEW.equals(addFolderAnswer[3]) ? addFolderAnswer[4] : addFolderAnswer[3];
        } else {
            javax.swing.JCheckBox p = new javax.swing.JCheckBox("Images (" + counts[0] + ")", counts[0] > 0);
            p.setName("media-pl-pictures");
            javax.swing.JCheckBox v = new javax.swing.JCheckBox("Videos (" + counts[1] + ")", counts[1] > 0);
            v.setName("media-pl-videos");
            javax.swing.JCheckBox m = new javax.swing.JCheckBox("Music (" + counts[2] + ")", counts[2] > 0);
            m.setName("media-pl-music");
            javax.swing.JComboBox<String> pick = new javax.swing.JComboBox<String>(names.toArray(new String[0]));
            pick.setName("media-pl-pick");
            JPanel box = new JPanel(new GridLayout(0, 1, 0, 4));
            box.add(p);
            box.add(v);
            box.add(m);
            box.add(new JLabel("Into the playlist"));
            box.add(pick);
            if (javax.swing.JOptionPane.showConfirmDialog(at, box, Playlist.ADD_FOLDER, javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE) != javax.swing.JOptionPane.OK_OPTION) return;
            pics = p.isSelected();
            vids = v.isSelected();
            music = m.isSelected();
            playlist = (String) pick.getSelectedItem();
            if (Playlist.ADD_NEW.equals(playlist)) {
                Object typed = javax.swing.JOptionPane.showInputDialog(at, "Playlist name", "New playlist", javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, dir.getName());
                playlist = typed == null ? null : typed.toString();
            }
        }
        if (playlist == null) return;
        if (!pics && !vids && !music) {
            lastAddFolderSaid = "Nothing added: no type is ticked";
        } else if (playlist.trim().length() == 0) {
            lastAddFolderSaid = "Nothing added: the playlist has no name";
        } else {
            List<String[]> files = Playlist.mediaOf(MediaDir.sortedBy(all, MediaDir.sortBy, MediaDir.sortDesc), pics, vids, music, e -> e.id);
            lastAddFolderSaid = files.isEmpty() ? "No files of the kinds ticked here" : Playlist.addAll(PromptDb.dir(), playlist.trim(), files);
        }
        app.setNow(lastAddFolderSaid);
    }

    /** A card while selecting: ☑ or ☐ before its name, outlined when ticked. */
    void paintPicked(JButton card, MediaDir.Entry entry) {
        boolean on = this.selected.contains(entry.id);
        String text = card.getText() == null ? "" : card.getText();
        text = text.replace("\u2611 ", "").replace("\u2610 ", "");
        card.setText(text.startsWith("<html>") ? "<html>" + (on ? "\u2611 " : "\u2610 ") + text.substring(6) : (on ? "\u2611 " : "\u2610 ") + text);
        card.setBorder(on ? BorderFactory.createLineBorder(new java.awt.Color(0x4A90E2), 3) : UIManagerBorder());
        if (this.selectionCount != null) this.selectionCount.setText("   " + this.selected.size() + " selected (right click a ticked one: Remove, Move)");
    }

    static javax.swing.border.Border UIManagerBorder() {
        javax.swing.border.Border b = javax.swing.UIManager.getBorder("Button.border");
        return b != null ? b : BorderFactory.createEmptyBorder(2, 2, 2, 2);
    }

    /** Search: a name (* and ?) and a type; false when cancelled. */
    boolean searchDialog(java.awt.Component at) {
        String[] got = searchAnswer;
        if (got == null) {
            javax.swing.JTextField field = new javax.swing.JTextField(this.search, 22);
            field.setName("media-search-field");
            String[] types = {"A all", "I pictures", "V videos", "S sounds", "T text", "O other"};
            javax.swing.JComboBox<String> type = new javax.swing.JComboBox<String>(types);
            type.setName("media-search-type");
            for (String t : types) if (t.startsWith(this.searchType + " ")) type.setSelectedItem(t);
            JPanel box = new JPanel(new GridLayout(0, 1, 0, 4));
            box.add(new JLabel("Name (* any letters, ? one letter; without them, names holding it):"));
            box.add(field);
            box.add(new JLabel("Type:"));
            box.add(type);
            if (javax.swing.JOptionPane.showConfirmDialog(at, box, "Search", javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE) != javax.swing.JOptionPane.OK_OPTION) return false;
            got = new String[] {field.getText(), ((String) type.getSelectedItem()).substring(0, 1)};
        }
        this.search = got[0] == null ? "" : got[0].trim();
        this.searchType = got.length > 1 && got[1] != null && got[1].length() > 0 ? got[1] : "A";
        return true;
    }

    /** A ticked file's right click: Remove selected (asked first), Move selected to…, Clear selection. */
    void selectionMenu(java.awt.Component card, int x, int y) {
        final List<File> picked = new ArrayList<File>();
        for (String id : this.selected) if (new File(id).isFile()) picked.add(new File(id));
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        m.setName("media-selection-menu");
        javax.swing.JMenuItem remove = new javax.swing.JMenuItem(REMOVE_SELECTED + " (" + picked.size() + ")");
        remove.setName("media-remove-selected");
        remove.addActionListener(e -> this.removeSelected(picked));
        m.add(remove);
        javax.swing.JMenuItem move = new javax.swing.JMenuItem(MOVE_SELECTED);
        move.setName("media-move-selected");
        move.addActionListener(e -> this.moveSelected(picked));
        m.add(move);
        javax.swing.JMenuItem clear = new javax.swing.JMenuItem("Clear selection");
        clear.addActionListener(e -> {
            this.selected.clear();
            this.refreshShown();
        });
        m.add(clear);
        MenuCancel.add(m);
        this.lastMenu = m;
        if (card.isShowing()) m.show(card, x, y);
    }

    /** What Remove selected / Move selected said last (for the tests). */
    static String lastSelectionSaid;

    void removeSelected(List<File> picked) {
        String yes = answer != null ? answer.apply(REMOVE_SELECTED)
            : javax.swing.JOptionPane.showOptionDialog(this.last, "Delete these " + picked.size() + (picked.size() == 1 ? " file" : " files") + "? They cannot be brought back.",
                REMOVE_SELECTED, javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE, null, new Object[] {"Delete", "Cancel"}, "Cancel") == 0 ? "yes" : null;
        if (!"yes".equals(yes)) return;
        int ok = 0;
        for (File f : picked) {
            String tagKey = MediaTags.key(f.getName(), f.length());
            if (f.delete()) {
                ok++;
                MediaTags.deleted(tagKey);
            }
        }
        this.selectionDone("Deleted", ok, picked.size());
    }

    void moveSelected(List<File> picked) {
        File to;
        if (answer != null) {
            String a = answer.apply(MOVE_SELECTED);
            if (a == null) return;
            to = new File(a);
        } else {
            javax.swing.JFileChooser pick = new javax.swing.JFileChooser(this.shown);
            pick.setDialogTitle("Move " + picked.size() + (picked.size() == 1 ? " file" : " files") + " to");
            pick.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
            if (pick.showDialog(this.last, "Move here") != javax.swing.JFileChooser.APPROVE_OPTION) return;
            to = pick.getSelectedFile();
        }
        int ok = 0;
        if (to.isDirectory() || to.mkdirs()) {
            for (File f : picked) {
                try {
                    MediaDir.moveInto(f, to);
                    ok++;
                } catch (Exception ignored) {
                    // counted as not moved
                }
            }
        }
        this.selectionDone("Moved", ok, picked.size());
    }

    void selectionDone(String verb, int ok, int all) {
        lastSelectionSaid = verb + " " + ok + (ok == 1 ? " file" : " files") + (ok < all ? "; " + (all - ok) + " could not be" : "");
        app.setNow(lastSelectionSaid);
        this.selected.clear();
        this.refreshShown();
    }

    /** The folder shown again. */
    void refreshShown() {
        if (this.last != null && this.last.isShowing() && this.shown != null) this.fill(this.last, this.shownRoot, this.shown);
    }

    void menu(final MediaDir.Entry entry, final File f, java.awt.Component card, int x, int y) {
        // Selecting: a ticked file's menu is the selection's.
        if (this.selecting) {
            if (this.selected.isEmpty()) this.selected.add(entry.id);
            this.selectionMenu(card, x, y);
            return;
        }
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.setName("media-menu");
        // A file already in the playlist: its third item takes it out.
        final boolean inList = this.listed.contains(f.getAbsolutePath());
        String[] built = MediaDir.menu(inList, entry.kind, this.chooser == null, entry.name, true);
        // A picture: Use as a reference (the Sogni picture tools shown); one to upscale is asked about first.
        final byte[] refHead = entry.kind == MediaDir.PICTURE ? MediaDir.head(f) : null;
        if (refHead != null && MediaDir.useRef(entry.name, refHead)) {
            built = java.util.Arrays.copyOf(built, built.length + 1);
            built[built.length - 1] = MediaDir.PROCESS_REF;
        }
        // A made file carrying its Sogni job id: Sogni job id (and the workflow that made it).
        final String job = MediaDir.sogniJob(f, entry.name);
        if (job != null) {
            built = java.util.Arrays.copyOf(built, built.length + 1);
            built[built.length - 1] = MediaDir.SOGNI_JOB;
        }
        // A favourite already: Remove from favourites.
        FileFavourites.load(PromptDb.dir());
        final boolean fav = FileFavourites.isKey(MediaDir.groupKey(entry));
        for (int i = 0; i < built.length; i++) if (fav && FileFavourites.ADD_SHORT.equals(built[i])) built[i] = FileFavourites.REMOVE_SHORT;
        // In alphabetical order; the first items are picked by their old place (slot).
        final String[] items = MediaDir.sorted(built);
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            final int slot = MediaDir.baseIndex(inList, entry.kind, items[i]);
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(items[i]);
            item.setName("media-menu:" + (slot >= 0 ? String.valueOf(slot) : items[i]));
            if (MediaDir.PROCESS_REF.equals(items[i])) {
                final File backRoot = this.shownRoot, backDir = this.shown;
                item.addActionListener(e -> {
                    String q = UpscaleCheck.refQuestion(PromptDb.dir(), entry.name, refHead);
                    if (q != null) {
                        Object[] options = {UpscaleCheck.UPSCALE_FIRST, UpscaleCheck.AS_IT_IS, "Cancel"};
                        int ans = javax.swing.JOptionPane.showOptionDialog(card, q, MediaDir.PROCESS_REF, javax.swing.JOptionPane.DEFAULT_OPTION,
                            javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                        if (ans == 0) {
                            app.setNow(this.menuPicked(entry, f, MediaDir.UPSCALE_ITEM));
                            return;
                        }
                        if (ans != 1) return;
                    }
                    ProcessRefDialog.open(app, f, () -> this.reopen(backRoot, backDir));
                });
                menu.add(item);
                continue;
            }
            if (MediaDir.SOGNI_JOB.equals(items[i])) {
                item.addActionListener(e -> SogniJobDialog.show(app, job));
                menu.add(item);
                continue;
            }
            if (FileFavourites.ADD_SHORT.equals(items[i]) || FileFavourites.REMOVE_SHORT.equals(items[i])) {
                item.setName("media-menu:favourite");
                item.addActionListener(e -> {
                    boolean now = FileFavourites.toggleKey(MediaDir.groupKey(entry));
                    app.setNow(entry.name + (now ? " added to favourites" : " taken out of favourites"));
                });
                menu.add(item);
                continue;
            }
            if (FileGroups.ADD.equals(items[i])) {
                // Add to group: the groups' list where the menu was.
                item.setName("media-menu:" + items[i]);
                item.addActionListener(e -> {
                    javax.swing.JPopupMenu groups = GroupMenus.addTo(app, MediaDir.groupKey(entry), entry.name, null);
                    if (card != null && card.isShowing()) groups.show(card, x, y);
                });
                menu.add(item);
                continue;
            }
            if (MediaTags.TAGS.equals(items[i]) || MediaTags.CATEGORY.equals(items[i])) {
                item.setName("media-menu:" + items[i]);
                item.addActionListener(e -> {
                    if (MediaTags.TAGS.equals(items[which])) this.tags(entry);
                    else this.category(entry);
                });
                menu.add(item);
                continue;
            }
            if (MediaDir.isKindItem(items[i])) {
                // A MIDI's editor, renders and player; a sound's Convert drum hits into MIDI.
                item.setName("media-menu:" + items[i]);
                item.addActionListener(e -> this.kindItem(entry, f, items[which]));
                menu.add(item);
                continue;
            }
            if (java.util.Arrays.asList(MediaDir.FILE_ACTIONS).contains(items[i])) {
                // Rename, Copy / Move to folder, Delete: asked about first, then the folder shown again.
                item.setName("media-menu:" + items[i]);
                item.addActionListener(e -> this.fileAction(entry, f, items[which]));
                menu.add(item);
                continue;
            }
            item.addActionListener(e -> {
                // A picture smaller than the last upscaled one, or not 4:7: asked first whether to upscale it.
                if ((slot == 0 || slot == 1) && entry.kind == MediaDir.PICTURE && !MediaDir.tooBig(f.length())) {
                    String q;
                    try {
                        q = UpscaleCheck.question(PromptDb.dir(), entry.name, Files.readAllBytes(f.toPath()));
                    } catch (Exception ex) {
                        q = null;
                    }
                    if (q != null) {
                        Object[] options = {"Upscale first", "Add as it is", "Cancel"};
                        int ans = javax.swing.JOptionPane.showOptionDialog(card, q, slot == 1 ? "Add to DB result files" : "Add to DB Reference files",
                            javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                        if (ans == 0) {
                            app.setNow(this.menuPicked(entry, f, MediaDir.UPSCALE_ITEM));
                            return;
                        }
                        if (ans != 1) return;
                    }
                }
                String said = this.menuPicked(entry, f, slot);
                app.setNow(said);
                // In the DB: then which group it goes in (No group: none).
                if ((slot == 0 || slot == 1) && said.contains(" is in the DB as a ")) GroupMenus.afterAdd(app, entry.name, f.length());
                // Added to (or taken out of) the playlist: its card's ☰ badge follows at once.
                if (slot == 2 && card instanceof JButton) {
                    JButton b = (JButton) card;
                    b.setText(this.caption(entry, b.getIcon() != null ? null : mark(entry)));
                }
            });
            menu.add(item);
        }
        // Add to playlist: a named one (.pls, File > Playlist viewer/editor), not only this folder's.
        if (entry.kind == MediaDir.PICTURE || entry.kind == MediaDir.VIDEO || entry.kind == MediaDir.SOUND) {
            // In its alphabetical place among the items.
            javax.swing.JMenu add = PlaylistWindow.addMenu(app, f.getAbsolutePath(), entry.name);
            int at = 0;
            while (at < items.length && MediaDir.sortKey(items[at]).compareTo(MediaDir.sortKey(add.getText())) < 0) at++;
            if (at <= menu.getComponentCount()) menu.insert(add, at);
            else menu.add(add);
        }
        // Add to Collection: a link to the file, in its alphabetical place among the items.
        javax.swing.JMenuItem collect = CollectionsWindow.addItem(app, card, MediaCollections.KIND_FILE, f.getAbsolutePath(), entry.name, f.length());
        int place = 0;
        while (place < menu.getComponentCount() && menu.getComponent(place) instanceof javax.swing.JMenuItem && !MenuCancel.is(menu.getComponent(place))
            && MediaDir.sortKey(((javax.swing.JMenuItem) menu.getComponent(place)).getText()).compareTo(MediaDir.sortKey(collect.getText())) < 0) place++;
        menu.insert(collect, place);
        this.lastMenu = menu;
        MenuCancel.add(menu);
        if (card.isShowing()) menu.show(card, x, y);
    }

    /**
     * A MIDI's Open in MIDI editor (Save writes <name>-edited.mid beside it), Render with SF2
     * (Midi2Wav), Render with plugin (vst_render.py), Play with default SoundFont (Java's own MIDI
     * synth, all instruments); a sound's Convert drum hits into MIDI (DrumMidi_CRT). Programs open
     * in PyJav with the file as their input, Params open, and Return comes back to this folder.
     */
    void kindItem(MediaDir.Entry entry, final File f, String item) {
        if (MediaDir.MIDI_EDIT.equals(item)) {
            byte[] bytes;
            try {
                bytes = java.nio.file.Files.readAllBytes(f.toPath());
            } catch (Exception ex) {
                app.setNow("Could not read " + entry.name);
                return;
            }
            final String edited = entry.name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + "-edited.mid";
            final File dir = this.shown;
            MidiEditScreen.openFile(app, entry.name, bytes, midi -> {
                String where = writeBeside(f, edited, midi);
                // The new file is listed at once.
                if (this.last != null && this.last.isShowing() && dir != null) this.open(dir);
                return where;
            });
            return;
        }
        if (MediaDir.PLAY_SF.equals(item)) {
            playDefault(app, f, entry.name);
            return;
        }
        if (MediaDir.TO_COMPOSER.equals(item)) {
            toComposer(app, f, entry.name);
            return;
        }
        if (MediaDir.TO_FILE_SET.equals(item)) {
            toFileSet(app, f, entry.name);
            return;
        }
        if (MediaDir.TO_WAV.equals(item)) {
            // The WAV beside the MP3; listed at once.
            File wav = app.pyJav.asWav(f);
            if (wav != f) app.setNow("Saved " + wav.getName() + " beside " + entry.name);
            if (this.last != null && this.last.isShowing() && this.shown != null) this.open(this.shown);
            return;
        }
        final File root = this.shownRoot;
        final File dir = this.shown;
        if (this.last != null) this.last.dispose();
        app.showView("py");
        if (MediaDir.RENDER_PLUGIN.equals(item)) app.programMenus.selectListedProgram("Python", "vst_render.py");
        else app.programMenus.selectListedProgram("Java", MediaDir.TO_MIDI.equals(item) ? "DrumMidi_CRT.jar" : "Midi2Wav.java");
        app.pyJav.setInputFile(f);
        app.pyJav.setReturn(() -> this.reopen(root, dir));
        SwingUtilities.invokeLater(() -> app.pyJav.openParams());
        app.setNow(item + ": " + entry.name);
    }

    /** Add to composer stack: the MIDI as a file set (no song asked about), its patterns and fills on the Composer's stack, the Composer shown. */
    /** Add into file set: the MIDI kept as the source MIDI of the file set picked (From file set finds it there). */
    static void toFileSet(Pulsekit app, File f, String name) {
        byte[] midi;
        try {
            midi = java.nio.file.Files.readAllBytes(f.toPath());
        } catch (Exception ex) {
            app.setNow("Could not read " + name);
            return;
        }
        java.util.List<String> sources = Engine.fileSetSources(app.learned, app.learnedFills);
        if (sources.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(app, "No file sets yet: import a pattern library, or run DrumMidi_CRT in PyJav (it makes one).", MediaDir.TO_FILE_SET, javax.swing.JOptionPane.PLAIN_MESSAGE);
            return;
        }
        Object picked = javax.swing.JOptionPane.showInputDialog(app, "File set for " + name, MediaDir.TO_FILE_SET, javax.swing.JOptionPane.PLAIN_MESSAGE, null,
            sources.toArray(new String[0]), sources.get(0));
        if (picked == null) return;
        Engine.rememberFileSetMidi(picked.toString(), midi, name);
        app.fileSets.storeAudioDir();
        app.setNow(name + " is the MIDI of the file set " + picked);
    }

    static void toComposer(Pulsekit app, File f, String name) {
        try {
            Engine.MidiBars bars = Engine.parseMidiBars(java.nio.file.Files.readAllBytes(f.toPath()));
            if (bars == null) {
                app.setNow(name + " has no drums for the stack");
                return;
            }
            app.importLibrary.quietSet = true;
            app.importLibrary.lastPackedSet = null;
            boolean made;
            try {
                made = app.importLibrary.learnFromSongImport(name, bars, true) && app.importLibrary.lastPackedSet != null;
            } finally {
                app.importLibrary.quietSet = false;
            }
            String set = app.importLibrary.lastPackedSet;
            if (!made) {
                app.setNow("No patterns in " + name + " for the stack");
                return;
            }
            Composer c = app.composerPage.composer();
            int added = c.addFileSet(set, app.learned, app.learnedFills);
            c.save(PromptDb.dir());
            app.showView("composer");
            app.setNow(name + " \u00b7 " + added + (added == 1 ? " block" : " blocks") + " on the Composer's stack");
        } catch (Exception ex) {
            app.setNow("Could not read " + name);
        }
    }

    /** `data` as `name` in the file's own folder (a name taken gets " (1)"); returns its path. */
    static String writeBeside(File file, String name, byte[] data) throws Exception {
        String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        String ext = name.substring(stem.length());
        File out = new File(file.getParentFile(), name);
        for (int n = 1; out.exists(); n++) out = new File(file.getParentFile(), stem + " (" + n + ")" + ext);
        java.nio.file.Files.write(out.toPath(), data);
        return out.getPath();
    }

    /** Play with default SoundFont: Java's own MIDI synth (its default sound bank), Stop in a small window. */
    static javax.sound.midi.Sequencer playing;
    static JDialog lastPlay;

    static void playDefault(Pulsekit app, File f, String name) {
        stopDefault();
        try {
            javax.sound.midi.Sequencer seq = javax.sound.midi.MidiSystem.getSequencer();
            seq.open();
            seq.setSequence(javax.sound.midi.MidiSystem.getSequence(f));
            seq.start();
            playing = seq;
        } catch (Exception ex) {
            app.setNow("Could not play " + name + " with the default SoundFont" + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            return;
        }
        app.setNow("Playing " + name + " with the default SoundFont");
        final JDialog d = new JDialog(app, name, false);
        d.setName("media-play-default");
        JButton stop = new JButton("Stop");
        stop.addActionListener(e -> d.dispose());
        JPanel p = new JPanel(new java.awt.BorderLayout(0, 8));
        p.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 16, 12, 16));
        p.add(new javax.swing.JLabel("Playing with the default SoundFont"), java.awt.BorderLayout.CENTER);
        p.add(stop, java.awt.BorderLayout.SOUTH);
        d.setContentPane(p);
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                stopDefault();
            }
        });
        d.pack();
        d.setLocationRelativeTo(app);
        d.setVisible(true);
        lastPlay = d;
    }

    static void stopDefault() {
        javax.sound.midi.Sequencer seq = playing;
        playing = null;
        if (seq == null) return;
        try {
            seq.stop();
            seq.close();
        } catch (Exception ignored) {
            // closed already
        }
    }

    /** Up from a folder: the folder to show again in the one above (outlined, scrolled to). */
    File focus;

    /** A folder's right-click menu: Rename, Delete. */
    void folderMenu(final MediaDir.Entry entry, final File f, java.awt.Component card, int x, int y, JDialog dialog, File root, File dir) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.setName("media-folder-menu");
        for (final String a : MediaDir.FOLDER_MENU) {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(a);
            item.setName("media-menu:" + a);
            item.addActionListener(e -> this.fileAction(entry, f, a));
            menu.add(item);
        }
        this.lastMenu = menu;
        MenuCancel.add(menu);
        if (card.isShowing()) menu.show(card, x, y);
    }

    /** The answers the tests give instead of the dialogs (a name, a folder, or "yes"); null: ask. */
    static java.util.function.Function<String, String> answer;

    private String ask(String question, String preset) {
        if (answer != null) return answer.apply(question);
        Object a = javax.swing.JOptionPane.showInputDialog(this.last, question, "Media browser", javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, preset);
        return a == null ? null : a.toString();
    }

    /** A library prompt file's right-click menu: Rename. */
    void dbMenu(final MediaDir.Entry entry, java.awt.Component on, int x, int y) {
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        m.setName("media-db-menu");
        javax.swing.JMenuItem rename = new javax.swing.JMenuItem(MediaDir.RENAME);
        rename.setName("media-db-rename");
        rename.addActionListener(e -> this.renameDb(entry));
        m.add(rename);
        MenuCancel.add(m);
        lastDbMenu = m;
        m.show(on, x, y);
    }

    /** The DB file menu shown last (for the tests). */
    static javax.swing.JPopupMenu lastDbMenu;

    /** A library prompt file's new name in the DB (its ending kept when none is typed), then the folder shown again. */
    void renameDb(MediaDir.Entry entry) {
        String typed = this.ask("New name for " + entry.name, entry.name);
        if (typed == null) return;
        String said;
        try {
            said = "Renamed " + entry.name + " to " + PromptDb.vault().renameFile(entry.dbId, PromptVault.PROMPT_FILE, MediaDir.keepEnding(entry.name, typed));
        } catch (Exception ex) {
            said = ex.getMessage() != null ? ex.getMessage() : "Could not rename " + entry.name;
        }
        this.app.setNow(said);
        if (this.last != null && this.last.isShowing() && this.shown != null) this.fill(this.last, this.shownRoot, this.shown);
    }

    /** Rename, Copy to folder, Move to folder or Delete a file (or a folder: Rename, Delete), then the folder shown again. */
    void fileAction(MediaDir.Entry entry, File f, String action) {
        String said;
        try {
            if (MediaDir.SEND_ZIP.equals(action)) {
                // The file, or the folder with its folders, into <name>.zip beside it.
                said = ZipFlow.sendFile(this.app, f);
                if (said == null) return;
                ZipFlow.lastSaid = said;
            } else if (MediaDir.RENAME.equals(action)) {
                String typed = answer != null ? this.ask("New name for " + f.getName(), f.getName()) : this.renameDialog(f);
                if (typed == null) return;
                String name = f.isDirectory() ? MediaDir.cleanName(typed) : MediaDir.keepEnding(f.getName(), typed);
                File to = name == null ? null : new File(f.getParentFile(), name);
                long size = f.length();
                said = name == null ? "Not a name: " + typed : to.exists() ? name + " is there already"
                    : f.renameTo(to) ? "Renamed " + f.getName() + " to " + name : "Could not rename " + f.getName();
                // Its tags and category go with the new name.
                if (said.startsWith("Renamed") && !to.isDirectory()) MediaTags.renamed(MediaTags.key(f.getName(), size), MediaTags.key(name, size));
            } else if (MediaDir.DELETE.equals(action)) {
                String yes = answer != null ? answer.apply("Delete " + f.getName())
                    : javax.swing.JOptionPane.showOptionDialog(this.last, f.isDirectory() ? "Delete " + f.getName() + " and everything in it?" : "Delete " + f.getName() + "?",
                        "Delete", javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE, null, new Object[] {"Delete", "Cancel"}, "Cancel") == 0 ? "yes" : null;
                if (!"yes".equals(yes)) return;
                String tagKey = f.isFile() ? MediaTags.key(f.getName(), f.length()) : null;
                said = MediaDir.deleteAll(f) ? "Deleted " + f.getName() : "Could not delete all of " + f.getName();
                if (tagKey != null && said.startsWith("Deleted")) MediaTags.deleted(tagKey);
            } else {
                boolean move = MediaDir.MOVE_TO.equals(action);
                File to;
                if (answer != null) {
                    String a = answer.apply((move ? "Move " : "Copy ") + f.getName() + " to");
                    if (a == null) return;
                    to = new File(a);
                } else {
                    javax.swing.JFileChooser pick = new javax.swing.JFileChooser(this.shown);
                    pick.setDialogTitle((move ? "Move " : "Copy ") + f.getName() + " to");
                    pick.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
                    if (pick.showDialog(this.last, move ? "Move here" : "Copy here") != javax.swing.JFileChooser.APPROVE_OPTION) return;
                    to = pick.getSelectedFile();
                }
                if (!to.isDirectory() && !to.mkdirs()) throw new java.io.IOException("could not make " + to.getName());
                File done = move ? MediaDir.moveInto(f, to) : MediaDir.copyInto(f, to);
                said = (move ? "Moved " : "Copied ") + f.getName() + " to " + done.getParentFile().getName();
            }
        } catch (Exception ex) {
            said = "Could not " + action.toLowerCase().replace("\u2026", "") + " " + f.getName() + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
        app.setNow(said);
        // The folder shown again.
        if (this.last != null && this.last.isShowing() && this.shown != null) this.fill(this.last, this.shownRoot, this.shown);
    }

    /** What Rename's two fields showed last {name, ending} (for the tests); the tests' answer {name, ending}. */
    static String[] lastRenameParts;
    static String[] renameAnswer;

    /** Rename: the name (focused, the cursor at its end) and the ending in their own fields; the new name, or null. */
    String renameDialog(File f) {
        final String[] parts = MediaDir.splitName(f.getName(), f.isDirectory());
        lastRenameParts = parts;
        if (renameAnswer != null) return parts[1].length() == 0 ? renameAnswer[0] : MediaDir.joinName(renameAnswer[0], renameAnswer[1]);
        final javax.swing.JTextField name = new javax.swing.JTextField(parts[0], 26);
        name.setName("media-rename-field");
        final javax.swing.JTextField ext = new javax.swing.JTextField(parts[1], 6);
        ext.setName("media-rename-ext");
        JPanel box = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        box.add(name);
        if (parts[1].length() > 0) {
            box.add(new JLabel("."));
            box.add(ext);
        }
        // The name focused, its cursor at the end, once the dialog shows.
        name.addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override
            public void ancestorAdded(javax.swing.event.AncestorEvent ev) {
                SwingUtilities.invokeLater(() -> {
                    name.requestFocusInWindow();
                    name.setCaretPosition(name.getText().length());
                });
            }

            @Override
            public void ancestorRemoved(javax.swing.event.AncestorEvent ev) {}

            @Override
            public void ancestorMoved(javax.swing.event.AncestorEvent ev) {}
        });
        if (javax.swing.JOptionPane.showConfirmDialog(this.last, box, "Rename " + f.getName(), javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE) != javax.swing.JOptionPane.OK_OPTION) return null;
        return parts[1].length() == 0 ? name.getText() : MediaDir.joinName(name.getText(), ext.getText());
    }

    /** Tags…: the file's tags, picked from those made so far, and new ones typed (a comma between) (TagDialogs). */
    void tags(MediaDir.Entry entry) {
        TagDialogs.tags(app, this.last, entry.name, entry.size, this::refill);
    }

    /** Category…: one of the prompt library's categories for the file, or none (TagDialogs). */
    void category(MediaDir.Entry entry) {
        TagDialogs.category(app, this.last, entry.name, entry.size, this::refill);
    }

    /** The folder shown again. */
    private void refill() {
        if (this.last != null && this.last.isShowing() && this.shown != null) this.fill(this.last, this.shownRoot, this.shown);
    }

    /** A file card that can be dragged (its file's path) onto a folder card. */
    private void dragSource(final JButton card, final File f) {
        card.setTransferHandler(new javax.swing.TransferHandler() {
            @Override
            public int getSourceActions(javax.swing.JComponent c) {
                return MOVE;
            }

            @Override
            protected java.awt.datatransfer.Transferable createTransferable(javax.swing.JComponent c) {
                return new java.awt.datatransfer.StringSelection(f.getAbsolutePath());
            }
        });
        card.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) return;
                card.getModel().setArmed(false);
                card.getModel().setPressed(false);
                card.getTransferHandler().exportAsDrag(card, e, javax.swing.TransferHandler.MOVE);
            }
        });
    }

    /** A folder card (or Up) a file card can be dropped on: the file is moved into `folder`. */
    private void dropTarget(javax.swing.JComponent target, final File folder, final JDialog dialog, final File root, final File dir) {
        target.setTransferHandler(new javax.swing.TransferHandler() {
            @Override
            public boolean canImport(javax.swing.TransferHandler.TransferSupport s) {
                return s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(javax.swing.TransferHandler.TransferSupport s) {
                try {
                    String path = (String) s.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
                    app.setNow(MediaBrowser.this.dropped(new File(path), folder));
                    javax.swing.SwingUtilities.invokeLater(() -> MediaBrowser.this.fill(dialog, root, dir));
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
    }

    /** A file dropped on a folder: moved into it. The status line. */
    String dropped(File f, File folder) {
        if (!f.isFile() || folder.equals(f.getParentFile())) return "Not moved";
        try {
            File done = MediaDir.moveInto(f, folder);
            return "Moved " + f.getName() + " to " + done.getParentFile().getName();
        } catch (Exception ex) {
            return "Could not move " + f.getName() + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    /** The first picture in folder `dir` (by name), or null: the folder's thumbnail. */
    static File firstPicture(File dir) {
        File[] kids = dir.listFiles();
        File best = null;
        if (kids == null) return null;
        for (File k : kids) {
            if (!k.isFile() || k.getName().startsWith(".") || MediaDir.kind(k.getName()) != MediaDir.PICTURE) continue;
            if (best == null || k.getName().compareToIgnoreCase(best.getName()) < 0) best = k;
        }
        return best;
    }

    /** What a menu item does: 0 and 1 add the file to the prompt library, 2 to the default playlist. Returns the status line. */
    String menuPicked(MediaDir.Entry entry, File f, int which) {
        if (which == MediaDir.UPSCALE_ITEM) {
            // Upscale/resize image: ImageUpscaler on PyJav with this picture as its input; its Params open.
            final File root = this.shownRoot;
            final File dir = this.shown;
            if (this.last != null) this.last.dispose();
            app.showView("py");
            app.programMenus.selectListedProgram("Java", "ImageUpscaler.java");
            app.pyJav.setInputFile(f);
            // Return: the Media browser again, in this folder.
            app.pyJav.setReturn(() -> this.reopen(root, dir));
            SwingUtilities.invokeLater(() -> app.pyJav.openParams());
            return "ImageUpscaler: " + entry.name;
        }
        if (which == 2 && this.listed.contains(f.getAbsolutePath())) {
            String said = MediaPlaylist.remove(PromptDb.dir(), this.shown.getAbsolutePath(), f.getAbsolutePath(), entry.name);
            this.listed.remove(f.getAbsolutePath());
            this.paintPlaylist();
            return said;
        }
        if (which == 2) {
            String said = MediaPlaylist.add(PromptDb.dir(), this.shown.getAbsolutePath(), f.getAbsolutePath(), entry.name, f.length());
            this.listed.add(f.getAbsolutePath());
            this.paintPlaylist();
            return said;
        }
        if (MediaDir.tooBig(f.length())) return entry.name + " is over the library's 128 MB, so it is not in the DB";
        try {
            return MediaDir.addToDb(PromptDb.dir(), entry.name, Files.readAllBytes(f.toPath()), which == 1);
        } catch (Exception ex) {
            return "Could not read " + entry.name;
        }
    }

    /** A library prompt file copied into the temp folder (for Choose file), or null when it cannot be read. */
    File promptFile(MediaDir.Entry entry) {
        try {
            byte[] bytes = PromptDb.vault().fileBytes(entry.dbId, PromptVault.PROMPT_FILE);
            File dir = new File(new File(System.getProperty("java.io.tmpdir", "."), "pulsekit-library"), "prompt-" + entry.dbId);
            dir.mkdirs();
            File out = new File(dir, entry.name.replace('/', '_').replace('\\', '_'));
            Files.write(out.toPath(), bytes);
            out.deleteOnExit();
            return out;
        } catch (Exception ex) {
            app.setNow("Could not read " + entry.name);
            return null;
        }
    }

    /** A library prompt file's text, in Preview. */
    void openPromptFile(MediaDir.Entry entry) {
        try {
            app.promptDb.preview(entry.name, PromptDb.vault().fileBytes(entry.dbId, PromptVault.PROMPT_FILE));
        } catch (Exception ex) {
            app.setNow("Could not open " + entry.name);
        }
    }

    /** A video of the folder's `videos` (at `at`; -1: not among them) played; its ‹ › open the one beside it over it. */
    void playVideo(final List<MediaDir.Entry> videos, final int at, MediaDir.Entry now, File nowFile) {
        // Played from its own file: VLC in the window, else the browser's player; Loop videos goes with it.
        app.promptDb.videoBack = at > 0;
        app.promptDb.videoOn = at >= 0 && at < videos.size() - 1;
        app.promptDb.videoStep = step -> {
            int to = at + step;
            if (at < 0 || to < 0 || to >= videos.size()) return;
            MediaDir.Entry next = videos.get(to);
            this.playVideo(videos, to, next, new File(next.id));
        };
        try {
            app.promptDb.videoFile(now.name, nowFile, MediaDir.loopVideos, true);
        } finally {
            app.promptDb.videoBack = false;
            app.promptDb.videoOn = false;
            app.promptDb.videoStep = null;
        }
    }

    /** A card's click: a picture full size, a video or a sound played. */
    void openEntry(MediaDir.Entry entry, File f) {
        try {
            if (entry.kind == MediaDir.VIDEO) {
                // The folder's videos, in its order: the player's ‹ › open the previous and the next one.
                List<MediaDir.Entry> videos = new ArrayList<MediaDir.Entry>();
                int at = -1;
                for (MediaDir.Entry x : this.shownEntries) {
                    if (x.kind != MediaDir.VIDEO || x.dbId != 0) continue;
                    if (x == entry || (x.id != null && x.id.equals(entry.id))) at = videos.size();
                    videos.add(x);
                }
                this.playVideo(videos, at, entry, f);
                return;
            }
            // A picture's folder: its Crop saves there.
            app.promptDb.previewFolder = f.getAbsoluteFile().getParentFile();
            try {
                app.promptDb.preview(entry.name, Files.readAllBytes(f.toPath()));
            } finally {
                app.promptDb.previewFolder = null;
            }
        } catch (Exception ex) {
            app.setNow("Could not open " + entry.name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
    }

    /** Loop videos and the player's last volume, zoom and speed (MediaDir.encode), kept in ~/.pulsekit/media-browser.txt. */
    static File settings() {
        return new File(PromptDb.dir(), "media-browser.txt");
    }

    static void loadLoop() {
        try {
            File f = settings();
            MediaDir.decode(f.isFile() ? new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        } catch (Exception ex) {
            MediaDir.decode(null);
        }
    }

    static void saveLoop() {
        try {
            File f = settings();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), MediaDir.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // kept for this session
        }
    }

    private static String key(File f) {
        return f.getAbsolutePath() + "|" + f.length() + "|" + f.lastModified();
    }

    private static ImageIcon scaled(java.awt.image.BufferedImage img) {
        if (img == null) return null;
        double s = Math.min(1.0, Math.min(CELL / (double) img.getWidth(), CELL / (double) img.getHeight()));
        int w = Math.max(1, (int) (img.getWidth() * s));
        int h = Math.max(1, (int) (img.getHeight() * s));
        return new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH));
    }

    /** A folder's or a file's type mark (FOLDER, MP4, WAV), where there is no thumbnail. */
    static String mark(MediaDir.Entry e) {
        if (e.folder) return "FOLDER";
        if (e.dbId != 0) return MediaDir.STAR + "PROMPT";
        int dot = e.name.lastIndexOf('.');
        return dot >= 0 ? e.name.substring(dot + 1).toUpperCase() : "FILE";
    }

    /** The files in the shown folder's playlist (they get a ☰ badge). */
    private final java.util.Set<String> listed = new java.util.HashSet<String>();

    private String caption(MediaDir.Entry e, String mark) {
        String badge = !e.folder && this.listed.contains(e.id)
            ? "<span style='background:#D9A441;color:#0A0B0C'>&nbsp;\u2630&nbsp;</span> " : "";
        String sub = e.folder ? "folder" : e.dbId != 0 ? "prompt file · DB" : e.kind == MediaDir.PICTURE ? "picture" : e.kind == MediaDir.VIDEO ? "video · ▶" : e.kind == MediaDir.SOUND ? "sound · ▶" : "file";
        if (!e.folder) sub += " · " + Math.max(1, e.size / 1024) + " KB";
        // Its resolution and aspect ratio, once read: "1920×1080 · 16:9".
        String size = e.folder ? null : this.sizes.get(e.id);
        if (size != null && size.length() > 0) sub += "<br>" + size;
        // Its tags and category.
        String tags = e.folder || e.dbId != 0 ? "" : MediaTags.line(MediaTags.key(e));
        if (tags.length() > 0) sub += "<br><span style='color:#D9A441'>" + esc(tags) + "</span>";
        // A favourite file: ★ before its name.
        FileFavourites.load(PromptDb.dir());
        String star = !e.folder && e.dbId == 0 && FileFavourites.isKey(MediaDir.groupKey(e)) ? MediaDir.STAR : "";
        return "<html><center>" + (mark == null ? "" : "<div style='font-size:18px;color:#8A8B86;padding:30px'>" + mark + "</div>")
            + "<div style='width:" + CELL + "px'>" + badge + star + esc(MediaDir.shownName(e)) + "</div><div style='font-size:9px;color:#8A8B86'>" + sub + "</div></center></html>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }
}
