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
    /** Videos over this are played from their own file, never read in whole. */
    private static final long READ_MAX = 256L * 1024 * 1024;

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

    /** Opens the Media browser on `dir` (the folder picked: Up stops there). */
    void open(File dir) {
        if (dir == null || !dir.isDirectory()) {
            app.setNow((dir == null ? "That" : dir.getPath()) + " is not a directory");
            return;
        }
        final JDialog dialog = new JDialog(app, "Media browser", true);
        dialog.setName("media-browser");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.last = dialog;
        this.fill(dialog, dir.getAbsoluteFile(), dir.getAbsoluteFile());
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
    }

    static List<MediaDir.Entry> list(File dir) {
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
        return MediaDir.shown(all);
    }

    /** The dialog's contents for `dir`; the thumbnails come in the background. */
    private void fill(final JDialog dialog, final File root, final File dir) {
        this.shown = dir;
        final AtomicBoolean gone = new AtomicBoolean();
        final List<MediaDir.Entry> entries = list(dir);
        dialog.setTitle("Media browser · " + MediaDir.label(dir.getPath()));
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new BorderLayout(8, 0));
        JLabel where = new JLabel("<html><b>" + esc(dir.getPath()) + "</b><br>" + MediaDir.summary(entries) + "</html>");
        where.setName("media-summary");
        top.add(where, BorderLayout.CENTER);
        if (!dir.equals(root) && dir.getParentFile() != null) {
            JButton up = new JButton("Up");
            up.setName("media-up");
            up.addActionListener(e -> {
                gone.set(true);
                this.fill(dialog, root, dir.getParentFile());
            });
            top.add(up, BorderLayout.WEST);
        }
        body.add(top, BorderLayout.NORTH);
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
                card.addActionListener(e -> this.openEntry(entry, f));
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

    /** A card's click: a picture full size, a video or a sound played. */
    void openEntry(MediaDir.Entry entry, File f) {
        try {
            if (entry.kind == MediaDir.VIDEO) {
                if (VlcPlayer.available()) {
                    try {
                        app.promptDb.vlcPreview(entry.name, f);
                        return;
                    } catch (Exception ex) {
                        app.setNow(ex.getMessage() != null ? ex.getMessage() : "VLC could not play " + entry.name);
                    }
                }
                if (f.length() > READ_MAX) {
                    java.awt.Desktop.getDesktop().open(f);
                    return;
                }
            }
            app.promptDb.preview(entry.name, Files.readAllBytes(f.toPath()));
        } catch (Exception ex) {
            app.setNow("Could not open " + entry.name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
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

    private static String caption(MediaDir.Entry e, String mark) {
        String sub = e.folder ? "folder" : e.kind == MediaDir.PICTURE ? "picture" : e.kind == MediaDir.VIDEO ? "video · ▶" : "sound · ▶";
        if (!e.folder) sub += " · " + Math.max(1, e.size / 1024) + " KB";
        return "<html><center>" + (mark == null ? "" : "<div style='font-size:18px;color:#8A8B86;padding:30px'>" + mark + "</div>")
            + "<div style='width:" + CELL + "px'>" + esc(e.name) + "</div><div style='font-size:9px;color:#8A8B86'>" + sub + "</div></center></html>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }
}
