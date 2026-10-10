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
     * opened last), Download and the favourite folders; the file clicked goes to `picked`. "Other…"
     * opens the system's file chooser (`other`).
     */
    void choose(java.util.function.Consumer<File> picked, Runnable other) {
        loadLoop();
        this.chooser = picked;
        this.other = other;
        this.tab = MediaDir.lastRoot.length() > 0 && new File(MediaDir.lastRoot).isDirectory() ? "recent" : "download";
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
                all.add(e);
            }
        }
        return MediaDir.shown(all, every);
    }

    /** The dialog's contents for `dir`; the thumbnails come in the background. */
    private void fill(final JDialog dialog, final File root, final File dir) {
        this.shown = dir;
        // Remembered: the folder picked and the folders opened under it, for the next time.
        final List<String> under = new ArrayList<String>();
        for (File f = dir; f != null && !f.equals(root); f = f.getParentFile()) under.add(0, f.getPath());
        // Choose file's Download and favourite tabs do not change the Recent MB folder.
        if (this.chooser == null || "recent".equals(this.tab)) MediaDir.remember(root.getPath(), under);
        saveLoop();
        final AtomicBoolean gone = new AtomicBoolean();
        final List<MediaDir.Entry> entries = list(dir, this.chooser != null);
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
        if (!dir.equals(root) && dir.getParentFile() != null) {
            JButton up = new JButton("Up");
            up.setName("media-up");
            up.addActionListener(e -> {
                gone.set(true);
                this.fill(dialog, root, dir.getParentFile());
            });
            top.add(up, BorderLayout.WEST);
        }
        // Add to favourites: this folder, as a tab in Choose file.
        JButton fav = new JButton("Add to favourites");
        fav.setName("media-favourite");
        fav.addActionListener(e -> {
            boolean added = MediaDir.addFavourite(root.getPath(), under);
            saveLoop();
            app.setNow(added ? MediaDir.label(dir.getPath()) + " is a favourite: a tab in Choose file" : MediaDir.label(dir.getPath()) + " is a favourite already");
            if (added && this.chooser != null) {
                gone.set(true);
                this.fill(dialog, root, dir);
            }
        });
        loopRow.add(fav, 0);
        if (this.chooser != null) {
            JPanel north = new JPanel(new BorderLayout(0, 6));
            north.add(this.tabs(dialog, gone), BorderLayout.NORTH);
            north.add(top, BorderLayout.CENTER);
            body.add(north, BorderLayout.NORTH);
        } else {
            body.add(top, BorderLayout.NORTH);
        }
        JPanel grid = new JPanel(new GridLayout(0, 4, 8, 8));
        if (entries.isEmpty()) grid.add(new JLabel("No pictures, videos or sounds here"));
        final List<JButton> waiting = new ArrayList<JButton>();
        final List<File> videos = new ArrayList<File>();
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
            } else {
                card.setToolTipText(entry.kind == MediaDir.PICTURE ? "Opens it full size" : "Plays it");
                // A right click, or holding the button down, opens its menu (a long press, as on the phone).
                final boolean[] held = new boolean[1];
                card.addActionListener(e -> {
                    if (held[0]) held[0] = false;
                    else if (this.chooser != null) {
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
                        if (e.isPopupTrigger()) MediaBrowser.this.menu(entry, f, card, e.getX(), e.getY());
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        if (this.hold != null) this.hold.stop();
                    }
                });
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
                int kind = MediaDir.kind(f.getName());
                ImageIcon icon = null;
                try {
                    if (kind == MediaDir.PICTURE) icon = scaled(javax.imageio.ImageIO.read(f));
                    else if (kind == MediaDir.VIDEO && vlc) {
                        byte[] jpeg = VlcPlayer.firstFrame(f, 320);
                        if (jpeg != null) icon = scaled(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpeg)));
                    }
                } catch (Throwable ex) {
                    icon = null;
                }
                if (icon == null) continue;
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

    private void put(JButton card, ImageIcon icon) {
        File f = (File) card.getClientProperty("media-file");
        MediaDir.Entry e = new MediaDir.Entry();
        e.name = f.getName();
        e.id = f.getAbsolutePath();
        e.size = f.length();
        e.kind = MediaDir.kind(e.name);
        card.setIcon(icon);
        card.setText(caption(e, null));
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
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
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

    /** A file's menu (MediaDir.MENU): Add to DB as a reference or result file, Add to default playlist. */
    void menu(final MediaDir.Entry entry, final File f, java.awt.Component card, int x, int y) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.setName("media-menu");
        // A file already in the playlist: its third item takes it out.
        String[] items = this.listed.contains(f.getAbsolutePath()) ? MediaDir.MENU_LISTED : MediaDir.MENU;
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(items[i]);
            item.setName("media-menu:" + i);
            item.addActionListener(e -> {
                app.setNow(this.menuPicked(entry, f, which));
                // Added to (or taken out of) the playlist: its card's ☰ badge follows at once.
                if (which == 2 && card instanceof JButton) {
                    JButton b = (JButton) card;
                    b.setText(this.caption(entry, b.getIcon() != null ? null : mark(entry)));
                }
            });
            menu.add(item);
        }
        this.lastMenu = menu;
        if (card.isShowing()) menu.show(card, x, y);
    }

    /** What a menu item does: 0 and 1 add the file to the prompt library, 2 to the default playlist. Returns the status line. */
    String menuPicked(MediaDir.Entry entry, File f, int which) {
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
        if (MediaDir.tooBig(f.length())) return entry.name + " is over the library's 16 MB, so it is not in the DB";
        try {
            return MediaDir.addToDb(PromptDb.dir(), entry.name, Files.readAllBytes(f.toPath()), which == 1);
        } catch (Exception ex) {
            return "Could not read " + entry.name;
        }
    }

    /** A card's click: a picture full size, a video or a sound played. */
    void openEntry(MediaDir.Entry entry, File f) {
        try {
            if (entry.kind == MediaDir.VIDEO) {
                // Played from its own file: VLC in the window, else the browser's player; Loop videos goes with it.
                app.promptDb.videoFile(entry.name, f, MediaDir.loopVideos, true);
                return;
            }
            app.promptDb.preview(entry.name, Files.readAllBytes(f.toPath()));
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
        int dot = e.name.lastIndexOf('.');
        return dot >= 0 ? e.name.substring(dot + 1).toUpperCase() : "FILE";
    }

    /** The files in the shown folder's playlist (they get a ☰ badge). */
    private final java.util.Set<String> listed = new java.util.HashSet<String>();

    private String caption(MediaDir.Entry e, String mark) {
        String badge = !e.folder && this.listed.contains(e.id)
            ? "<span style='background:#D9A441;color:#0A0B0C'>&nbsp;\u2630&nbsp;</span> " : "";
        String sub = e.folder ? "folder" : e.kind == MediaDir.PICTURE ? "picture" : e.kind == MediaDir.VIDEO ? "video · ▶" : "sound · ▶";
        if (!e.folder) sub += " · " + Math.max(1, e.size / 1024) + " KB";
        return "<html><center>" + (mark == null ? "" : "<div style='font-size:18px;color:#8A8B86;padding:30px'>" + mark + "</div>")
            + "<div style='width:" + CELL + "px'>" + badge + esc(e.name) + "</div><div style='font-size:9px;color:#8A8B86'>" + sub + "</div></center></html>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }
}
