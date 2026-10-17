package pulsekit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * File > Downloads: the Sogni runs and the files they downloaded (SogniDownloads), in three tabs:
 * Pending, In progress, Finished / failed; by program, the newest first. A click on a file previews
 * it; a right click on a finished one offers Preview, Clear (off the list; the file stays), Rename,
 * Delete (asked first, with its prompt file and prompt library entry too when ticked), Move to and
 * Copy to (Downloads, the Recent MB folder, a favourite folder, or another folder picked).
 */
final class DownloadsScreen {
    /** The tab shown. */
    static String tab = "";

    private DownloadsScreen() {}

    static void show(final Pulsekit app) {
        SogniDownloads.init(PromptDb.dir());
        // The tab shown last, unless it is empty now (a run in progress is shown then).
        if (tab.length() == 0 || SogniDownloads.tab(tab).isEmpty()) tab = SogniDownloads.firstTab();
        final JDialog dialog = new JDialog(app, "Downloads", true);
        dialog.setName("downloads");
        final JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setPreferredSize(new Dimension(640, 480));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(scroll, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        south.add(close);
        content.add(south, BorderLayout.SOUTH);
        fill(app, body);
        // Every second: the tabs' counts and the runs' times; the list again when a run moved on.
        final String[] seen = new String[] {signature()};
        final javax.swing.Timer tick = new javax.swing.Timer(1000, e -> {
            String now = signature();
            if (!now.equals(seen[0])) {
                seen[0] = now;
                fill(app, body);
            } else {
                refreshTimes(body);
            }
        });
        tick.start();
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                tick.stop();
            }
        });
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
        tick.stop();
    }

    /** The runs and their states: when it changes, the list is made again. */
    static String signature() {
        StringBuilder sb = new StringBuilder();
        for (String t : new String[] {SogniDownloads.PENDING, SogniDownloads.PROGRESS, SogniDownloads.FINISHED}) {
            for (SogniDownloads.Run r : SogniDownloads.tab(t)) sb.append(r.id).append(r.state).append(r.status).append(r.items.size()).append(',');
            sb.append('|');
        }
        return sb.toString();
    }

    /** The In progress and Pending runs' lines again (their time). */
    static void refreshTimes(java.awt.Container c) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof JLabel && k.getName() != null && k.getName().startsWith("downloads-run:")) {
                SogniDownloads.Run r = SogniDownloads.run(Long.parseLong(k.getName().substring(14)));
                if (r != null) ((JLabel) k).setText(SogniDownloads.runLabel(r));
            } else if (k instanceof java.awt.Container) {
                refreshTimes((java.awt.Container) k);
            }
        }
    }

    static void fill(final Pulsekit app, final JPanel body) {
        body.removeAll();
        if (SogniDownloads.count() == 0) {
            JLabel none = new JLabel("No downloads yet");
            none.setName("downloads-none");
            body.add(none);
            body.revalidate();
            body.repaint();
            return;
        }
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        tabs.setAlignmentX(0.0f);
        for (final String t : new String[] {SogniDownloads.PENDING, SogniDownloads.PROGRESS, SogniDownloads.FINISHED}) {
            JToggleButton b = new JToggleButton(SogniDownloads.tabLabel(t), t.equals(tab));
            b.setName("downloads-tab:" + t);
            b.addActionListener(e -> {
                tab = t;
                fill(app, body);
            });
            tabs.add(b);
        }
        body.add(tabs);
        List<SogniDownloads.Run> runs = SogniDownloads.tab(tab);
        if (SogniDownloads.FINISHED.equals(tab) && !runs.isEmpty()) {
            // Sort by (newest first at first), and Clear all: the list, not the files.
            JPanel sorts = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            sorts.setAlignmentX(0.0f);
            sorts.add(new JLabel("Sort by"));
            for (final String how : SogniDownloads.SORTS) {
                JToggleButton b = new JToggleButton(how, how.equals(SogniDownloads.sort));
                b.setName("downloads-sort:" + how);
                b.addActionListener(e -> {
                    SogniDownloads.sort = how;
                    fill(app, body);
                });
                sorts.add(b);
            }
            JButton clearAll = new JButton("Clear all");
            clearAll.setName("downloads-clear-all");
            clearAll.addActionListener(e -> {
                Object[] options = {"Clear all", "Cancel"};
                if (JOptionPane.showOptionDialog(app, "Take every finished download off this list? The files stay where they are.", "Clear all",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) != 0) return;
                app.setNow(SogniDownloads.clearFinished() + " off the list (the files stay)");
                fill(app, body);
            });
            sorts.add(clearAll);
            body.add(sorts);
        }
        if (runs.isEmpty()) {
            JLabel none = new JLabel(SogniDownloads.PROGRESS.equals(tab) ? "No run in progress" : SogniDownloads.PENDING.equals(tab) ? "Nothing pending" : "No finished downloads");
            none.setName("downloads-empty");
            body.add(none);
        }
        String program = null;
        boolean headings = SogniDownloads.byProgram(tab);
        for (final SogniDownloads.Run r : runs) {
            if (headings && !r.programName().equals(program)) {
                program = r.programName();
                JLabel head = new JLabel("<html><b>" + program + "</b></html>");
                head.setName("downloads-program:" + program);
                head.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
                head.setAlignmentX(0.0f);
                body.add(head);
            }
            // Without program headings (Sort by newest, oldest, type) each run says its program.
            JLabel line = new JLabel((headings ? "" : r.programName() + " \u00b7 ") + SogniDownloads.runLabel(r));
            line.setName("downloads-run:" + r.id);
            line.setAlignmentX(0.0f);
            if (SogniDownloads.PROGRESS.equals(r.state) || SogniDownloads.PENDING.equals(r.state)) {
                // Mark as ready: a run the app lost track of (it never says it is over) is let go.
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                row.setAlignmentX(0.0f);
                row.add(line);
                row.add(Box.createHorizontalStrut(8));
                JButton ready = new JButton("Mark as ready");
                ready.setName("downloads-ready:" + r.id);
                ready.setToolTipText("Frees the run: it goes to Finished / failed (nothing is deleted)");
                ready.addActionListener(e -> {
                    SogniDownloads.markReady(r.id);
                    fill(app, body);
                });
                row.add(ready);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
                body.add(row);
                for (final SogniDownloads.Item item : r.items) body.add(itemButton(app, body, r, item));
                continue;
            }
            if (SogniDownloads.FAILED.equals(r.state) || r.items.isEmpty()) {
                line.setToolTipText("A right click takes it off the list");
                line.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        if (!SwingUtilities.isRightMouseButton(e)) return;
                        SogniDownloads.clear(r.id, null);
                        fill(app, body);
                    }
                });
            }
            body.add(line);
            for (final SogniDownloads.Item item : r.items) body.add(itemButton(app, body, r, item));
        }
        body.revalidate();
        body.repaint();
    }

    /** A downloaded file: its name and where it is, with a preview thumbnail when it is a picture (or a video VLC can read). */
    static java.awt.Component itemButton(final Pulsekit app, final JPanel body, final SogniDownloads.Run r, final SogniDownloads.Item item) {
        final JButton b = new JButton("<html>" + esc(item.name) + "<br><small>" + esc(item.shown) + "</small></html>");
        b.setName("downloads-item:" + item.name);
        b.setHorizontalAlignment(JButton.LEFT);
        b.setAlignmentX(0.0f);
        b.addActionListener(e -> preview(app, item));
        if (SogniDownloads.FINISHED.equals(r.state)) {
            javax.swing.ImageIcon icon = thumb(item);
            if (icon != null) {
                b.setIcon(icon);
                b.setIconTextGap(10);
                b.putClientProperty("downloads-thumb", Boolean.TRUE);
            }
        }
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
        if (SogniDownloads.FINISHED.equals(tab)) {
            b.setToolTipText("A right click: Preview, Clear, Rename, Delete, Move to, Copy to");
            b.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) menu(app, body, r, item).show(b, e.getX(), e.getY());
                }
            });
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setAlignmentX(0.0f);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
        wrap.add(b, BorderLayout.CENTER);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height + 3));
        return wrap;
    }

    /** Thumbnails made this session, by file and its time. */
    private static final java.util.Map<String, javax.swing.ImageIcon> THUMBS = new java.util.concurrent.ConcurrentHashMap<String, javax.swing.ImageIcon>();
    static final int THUMB = 64;

    /** A finished file's thumbnail (THUMB px): a picture, or a video's first frame with VLC; null otherwise. */
    static javax.swing.ImageIcon thumb(SogniDownloads.Item item) {
        File f = new File(item.where);
        if (!f.isFile()) return null;
        String key = f.getAbsolutePath() + "@" + f.lastModified();
        javax.swing.ImageIcon have = THUMBS.get(key);
        if (have != null) return have;
        try {
            java.awt.image.BufferedImage img = null;
            String kind = item.kind();
            if ("picture".equals(kind)) img = javax.imageio.ImageIO.read(f);
            else if ("video".equals(kind) && VlcPlayer.available()) {
                byte[] jpeg = VlcPlayer.firstFrame(f, 160);
                if (jpeg != null) img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
            }
            if (img == null) return null;
            double sc = Math.min(THUMB / (double) img.getWidth(), THUMB / (double) img.getHeight());
            javax.swing.ImageIcon icon = new javax.swing.ImageIcon(img.getScaledInstance(Math.max(1, (int) (img.getWidth() * sc)), Math.max(1, (int) (img.getHeight() * sc)), java.awt.Image.SCALE_SMOOTH));
            THUMBS.put(key, icon);
            return icon;
        } catch (Throwable ex) {
            return null;
        }
    }

    static final String[] MENU = {"Preview", "Clear", "Rename", "Delete", "Move to…", "Copy to…", "Create again", "Add to favourites"};

    /** Create again: the program that made the download on PyJav, with the arguments it ran with, its Params open. */
    static void createAgain(Pulsekit app, SogniDownloads.Run r) {
        String kind = r.program.toLowerCase(java.util.Locale.ROOT).endsWith(".py") ? "Python" : "Java";
        app.showView("py");
        app.programMenus.selectListedProgram(kind, r.program);
        if (!r.program.equals(app.pyName)) {
            app.setNow("Could not open " + r.program);
            return;
        }
        if (r.args.length() > 0 && app.pyJav.pyExtra != null) app.pyJav.pyExtra.setText(r.args);
        app.setNow(r.args.length() > 0 ? r.programName() + " with the arguments it ran with" : r.programName() + " (this download kept no arguments)");
        javax.swing.SwingUtilities.invokeLater(() -> app.pyJav.openParams());
    }

    /** Add to favourites: the prompt library version the run stored (its result file with it) among the favourite prompts. */
    static String favourite(SogniDownloads.Run r) {
        try {
            PromptVault vault = PromptDb.vault();
            PromptVault.Version v = r.versionId == 0 ? null : vault.version(r.versionId);
            if (v == null) return "Not in the prompt library: a run with Save the prompt into DB is (its result file with it)";
            VersionFavourites.load(PromptDb.dir());
            if (VersionFavourites.is(v.id)) return VersionFavourites.label(vault, v) + " is a favourite already";
            VersionFavourites.add(v.id);
            return "Added " + VersionFavourites.label(vault, v) + " to favourites";
        } catch (Exception ex) {
            return "Could not open the prompt library";
        }
    }

    /** A finished file's menu. */
    static JPopupMenu menu(final Pulsekit app, final JPanel body, final SogniDownloads.Run r, final SogniDownloads.Item item) {
        JPopupMenu m = new JPopupMenu();
        for (int i = 0; i < MENU.length; i++) {
            final int which = i;
            JMenuItem it = new JMenuItem(MENU[i]);
            it.setName("downloads-menu:" + MENU[i]);
            it.addActionListener(e -> {
                if (which == 6) {
                    createAgain(app, r);
                    return;
                }
                if (which == 7) app.setNow(favourite(r));
                else if (which == 0) preview(app, item);
                else if (which == 1) app.setNow(clear(r, item));
                else if (which == 2) {
                    String next = JOptionPane.showInputDialog(app, "New name", item.name);
                    if (next != null) app.setNow(rename(item, next));
                } else if (which == 3) {
                    JPanel ask = new JPanel(new BorderLayout(0, 6));
                    ask.add(new JLabel("Delete " + item.name + " from " + item.shown + "?"), BorderLayout.NORTH);
                    JCheckBox also = new JCheckBox("Also delete its prompt file" + (r.sheetName.length() > 0 ? " (" + r.sheetName + ")" : "") + " and its prompt library entry");
                    also.setName("downloads-delete-also");
                    if (r.sheet.length() > 0 || r.versionId != 0) ask.add(also, BorderLayout.CENTER);
                    Object[] options = {"Delete", "Cancel"};
                    int ans = JOptionPane.showOptionDialog(app, ask, "Delete", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
                    if (ans == 0) app.setNow(delete(r, item, also.isSelected()));
                } else {
                    File to = place(app, which == 4);
                    if (to != null) app.setNow(put(r, item, to, which == 4));
                }
                fill(app, body);
            });
            m.add(it);
        }
        return m;
    }

    /** Move to / Copy to: Downloads, the Recent MB folder, a favourite, or another folder (Other…); null when cancelled. */
    static File place(Pulsekit app, boolean move) {
        MediaBrowser.loadLoop();
        List<File> folders = new ArrayList<File>();
        List<String> names = new ArrayList<String>();
        folders.add(MediaBrowser.downloads());
        names.add("Downloads");
        if (MediaDir.lastRoot.length() > 0 && new File(MediaDir.lastRoot).isDirectory()) {
            File at = new File(MediaDir.lastRoot);
            for (String u : MediaDir.lastPath) if (new File(u).isDirectory()) at = new File(u);
            folders.add(at);
            names.add("Recent MB folder (" + at.getName() + ")");
        }
        for (String f : MediaDir.favourites) {
            String[] part = f.split("\t");
            File at = new File(part[part.length - 1]);
            if (!at.isDirectory()) continue;
            folders.add(at);
            names.add(MediaDir.favouriteLabel(f));
        }
        names.add("Other…");
        Object picked = JOptionPane.showInputDialog(app, move ? "Move to" : "Copy to", move ? "Move to" : "Copy to", JOptionPane.PLAIN_MESSAGE, null, names.toArray(), names.get(0));
        if (picked == null) return null;
        int i = names.indexOf(picked);
        if (i < folders.size()) return folders.get(i);
        JFileChooser chooser = new JFileChooser(folders.get(0));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(move ? "Move to" : "Copy to");
        return chooser.showOpenDialog(app) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    static void preview(Pulsekit app, SogniDownloads.Item item) {
        File f = new File(item.where);
        if (!f.isFile()) {
            app.setNow(item.name + " is gone from " + item.shown);
            return;
        }
        String kind = item.kind();
        if ("picture".equals(kind)) {
            List<File> one = new ArrayList<File>();
            one.add(f);
            app.pyJav.offerPictures(one);
        } else if ("video".equals(kind)) {
            app.pyJav.offerVideo(f, 1);
        } else if ("audio".equals(kind)) {
            app.pyJav.offerAudio(f);
        } else if ("text".equals(kind)) {
            try {
                JTextArea t = new JTextArea(new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8), 20, 60);
                t.setEditable(false);
                t.setLineWrap(true);
                t.setWrapStyleWord(true);
                JOptionPane.showMessageDialog(app, new JScrollPane(t), item.name, JOptionPane.PLAIN_MESSAGE);
            } catch (Exception ex) {
                app.setNow("Could not read " + item.name);
            }
        } else {
            try {
                java.awt.Desktop.getDesktop().open(f);
            } catch (Exception ex) {
                app.setNow("No preview for " + item.name);
            }
        }
    }

    /** Clear: off the list, the file stays. */
    static String clear(SogniDownloads.Run r, SogniDownloads.Item item) {
        SogniDownloads.clear(r.id, item);
        return item.name + " is off the list (the file stays)";
    }

    static String rename(SogniDownloads.Item item, String next) {
        String n = next == null ? "" : next.trim().replace('/', '_').replace('\\', '_');
        if (n.length() == 0 || n.equals(item.name)) return "Not renamed";
        File f = new File(item.where);
        File to = new File(f.getParentFile(), n);
        if (to.exists()) return "Could not rename " + item.name + ": " + n + " is there already";
        if (!f.renameTo(to)) return "Could not rename " + item.name;
        SogniDownloads.moved(item, n, to.getAbsolutePath(), to.getAbsolutePath());
        return "Renamed · " + n;
    }

    /** Delete: the file, and with `also` the run's prompt sheet and its prompt library version. */
    static String delete(SogniDownloads.Run r, SogniDownloads.Item item, boolean also) {
        File f = new File(item.where);
        if (f.exists() && !f.delete()) return "Could not delete " + item.name;
        StringBuilder said = new StringBuilder("Deleted · " + item.name);
        if (also) {
            if (r.sheet.length() > 0 && new File(r.sheet).delete()) said.append(", ").append(r.sheetName);
            if (r.versionId != 0) {
                try {
                    PromptDb.vault().deleteVersion(r.versionId);
                    said.append(", its prompt library entry");
                } catch (Exception ex) {
                    said.append(" (its prompt library entry stays: ").append(ex.getMessage()).append(')');
                }
            }
            SogniDownloads.unlinked(r);
        }
        SogniDownloads.clear(r.id, item);
        return said.toString();
    }

    /** Move to / Copy to `dir`; a name taken there gets " (1)". */
    static String put(SogniDownloads.Run r, SogniDownloads.Item item, File dir, boolean move) {
        File f = new File(item.where);
        if (!f.isFile()) return item.name + " is gone from " + item.shown;
        String stem = item.name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        String ext = item.name.substring(stem.length());
        File to = new File(dir, item.name);
        for (int n = 1; to.exists(); n++) to = new File(dir, stem + " (" + n + ")" + ext);
        try {
            if (move) Files.move(f.toPath(), to.toPath());
            else Files.copy(f.toPath(), to.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        } catch (Exception ex) {
            return "Could not " + (move ? "move " : "copy ") + item.name + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
        }
        if (move) SogniDownloads.moved(item, to.getName(), to.getAbsolutePath(), to.getAbsolutePath());
        return (move ? "Moved to " : "Copied to ") + to.getPath();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
