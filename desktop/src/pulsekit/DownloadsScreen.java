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
        // The window's X closes it as Close does: what it plays stops (hidden, it went on playing).
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
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
                if (r != null) ((JLabel) k).setText(SogniDownloads.caption(r, Boolean.TRUE.equals(((JLabel) k).getClientProperty("program")), SogniDownloads.tab(tab)));
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
                if (JOptionPane.showOptionDialog(app, DownloadMove.clearAllAsk(SogniDownloads.notMoved()), "Clear all",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) != 0) return;
                app.setNow(SogniDownloads.clearFinished() + " off the list (the files stay)");
                fill(app, body);
            });
            sorts.add(clearAll);
            // Clear automatically: after each move (Move now, Move automatically, the Media browser's),
            // the downloads moved taken off the list (the files stay in their folders).
            DownloadMove.load();
            final javax.swing.JCheckBox clearAuto = new javax.swing.JCheckBox(DownloadMove.CLEAR_AUTO, DownloadMove.clearAuto);
            clearAuto.setName("downloads-clear-auto");
            clearAuto.addActionListener(e -> {
                DownloadMove.clearAuto = clearAuto.isSelected();
                DownloadMove.save();
            });
            sorts.add(clearAuto);
            body.add(sorts);
            body.add(movePanel(app, body));
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
            // The caption opens and closes the run's files (and says how many there are).
            final boolean open = SogniDownloads.isOpen(r, runs);
            final JLabel line = new JLabel(SogniDownloads.caption(r, !headings, runs));
            line.setName("downloads-run:" + r.id);
            line.putClientProperty("program", !headings);
            line.setAlignmentX(0.0f);
            if (!SogniDownloads.shownItems(r).isEmpty()) {
                line.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                line.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        if (!SwingUtilities.isLeftMouseButton(e)) return;
                        SogniDownloads.toggle(r, runs);
                        fill(app, body);
                    }
                });
            }
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
                if (open) for (final SogniDownloads.Item item : SogniDownloads.shownItems(r)) body.add(itemButton(app, body, r, item));
                continue;
            }
            if (SogniDownloads.FAILED.equals(r.state) || r.items.isEmpty()) {
                line.setToolTipText(SogniDownloads.canResume(r) ? "A right click: Resume download, Clear" : "A right click: Clear (off the list)");
                line.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        if (!SwingUtilities.isRightMouseButton(e)) return;
                        runMenu(app, body, r, line, e.getX(), e.getY());
                    }
                });
            }
            body.add(line);
            if (SogniDownloads.canResume(r)) {
                // Pulsekit was closed (or the run failed) while the workflow went on on Sogni: fetch it.
                JButton resume = new JButton(SogniDownloads.RESUME);
                resume.setName("downloads-resume:" + r.id);
                resume.addActionListener(e -> resume(app, r));
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                row.add(resume);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
                body.add(row);
            }
            if (open) for (final SogniDownloads.Item item : SogniDownloads.shownItems(r)) body.add(itemButton(app, body, r, item));
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

    /** A failed run's right-click menu shown last (for the tests). */
    static javax.swing.JPopupMenu lastRunMenu;

    /** A failed run's menu: Resume download when its workflow is known, Clear. */
    static void runMenu(final Pulsekit app, final JPanel body, final SogniDownloads.Run r, java.awt.Component at, int x, int y) {
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        if (SogniDownloads.canResume(r)) {
            javax.swing.JMenuItem resume = new javax.swing.JMenuItem(SogniDownloads.RESUME);
            resume.setName("downloads-run-resume");
            resume.addActionListener(e -> resume(app, r));
            m.add(resume);
        }
        if (r.workflow.length() > 0) {
            javax.swing.JMenuItem copy = new javax.swing.JMenuItem("Copy workflow id");
            copy.setName("downloads-run-copy-workflow");
            copy.addActionListener(e -> {
                app.pyJav.workflowId = r.workflow;
                app.pyJav.copyWorkflowId();
            });
            m.add(copy);
        }
        javax.swing.JMenuItem clear = new javax.swing.JMenuItem("Clear");
        clear.setName("downloads-run-clear");
        clear.addActionListener(e -> {
            SogniDownloads.clear(r.id, null);
            fill(app, body);
        });
        m.add(clear);
        MenuCancel.add(m);
        lastRunMenu = m;
        if (at != null && at.isShowing()) m.show(at, x, y);
    }

    /**
     * Resume download: the program that ran on PyJav, run again with the arguments it ran with and
     * --workflow <its workflow>: it waits for that workflow (or fetches what it made) instead of
     * starting (and paying for) a new run. The same Downloads entry goes on.
     */
    static void resume(Pulsekit app, SogniDownloads.Run r) {
        if (!SogniDownloads.canResume(r)) return;
        String kind = r.program.toLowerCase(java.util.Locale.ROOT).endsWith(".py") ? "Python" : "Java";
        for (java.awt.Window w : java.awt.Window.getWindows()) if (w instanceof javax.swing.JDialog && "downloads".equals(w.getName())) w.dispose();
        app.showView("py");
        app.programMenus.selectListedProgram(kind, r.program);
        if (!r.program.equals(app.pyName)) {
            app.setNow("Could not open " + r.program);
            return;
        }
        if (r.args.length() > 0 && app.pyJav.pyExtra != null) app.pyJav.pyExtra.setText(r.args);
        String note = SogniDownloads.resuming(r.id);
        app.pyJav.forcedArgs = SogniDownloads.resumeArgs(r);
        app.pyJav.reconnectDownload = r.id;
        app.pyJav.reconnectNote = note;
        app.setNow(note);
        app.pyJav.runPython();
    }

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
                else if (which == 1) {
                    // Not moved out of Download yet: asked first.
                    Object[] options = {"Clear", "Cancel"};
                    if (!item.away && JOptionPane.showOptionDialog(app, DownloadMove.clearOneAsk(item.name), "Clear",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) != 0) return;
                    app.setNow(clear(r, item));
                }
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
        // Every file: Add to Collection and Add to playlist… after the download's own items.
        final File f = new File(item.where);
        m.add(CollectionsWindow.addItem(app, app, MediaCollections.KIND_FILE, f.getAbsolutePath(), item.name, f.length()));
        m.add(PlaylistWindow.addMenu(app, f.getAbsolutePath(), item.name));
        // An MP4 video: Extract audio (its sound as <video>-audio.m4a, copied as it is).
        if (Mp4Media.extractable(item.name)) {
            JMenuItem ex = new JMenuItem(Mp4Media.EXTRACT);
            ex.setName("downloads-menu:" + Mp4Media.EXTRACT);
            ex.addActionListener(e -> {
                if (!f.isFile()) app.setNow(item.name + " is gone from " + item.shown);
                else AudioExtract.run(app, f, item.name, app.pyJav.programFolder());
            });
            m.add(ex);
            JMenuItem rr = new JMenuItem(VideoFit.RERENDER);
            rr.setName("downloads-menu:" + VideoFit.RERENDER);
            rr.addActionListener(e -> {
                if (!f.isFile()) app.setNow(item.name + " is gone from " + item.shown);
                else VideoRender.ask(app, app, f, item.name, app.pyJav.programFolder(), true);
            });
            m.add(rr);
        }
        // A video: its first frame as a reference; a picture: as a reference (upscaled first when asked).
        if (MediaDir.firstFrameRef(item.name)) {
            JMenuItem it = new JMenuItem(MediaDir.FIRST_FRAME_AS_REF);
            it.setName("downloads-menu:" + MediaDir.FIRST_FRAME_AS_REF);
            it.addActionListener(e -> {
                if (!f.isFile()) app.setNow(item.name + " is gone from " + item.shown);
                else RefImages.firstFrameAsRef(app, f, null, () -> app.showView("downloads"));
            });
            m.add(it);
        } else if ("picture".equals(item.kind()) && MediaDir.useRef(item.name, MediaDir.head(f))) {
            JMenuItem it = new JMenuItem(MediaDir.PROCESS_REF);
            it.setName("downloads-menu:" + MediaDir.PROCESS_REF);
            it.addActionListener(e -> RefImages.offer(app, f, null, () -> app.showView("downloads")));
            m.add(it);
        }
        MenuCancel.add(m);
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
            if (SoundClip.plays(f)) SoundClip.window(app, f);
            else try {
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
        if (move) {
            SogniDownloads.movedAway(item, to.getName(), to.getAbsolutePath(), to.getAbsolutePath());
            // The playlists' entries for it follow it.
            Playlist.repoint(PromptDb.dir(), java.util.Collections.singleton(f.getAbsolutePath()), to.getAbsolutePath());
        }
        return (move ? "Moved to " : "Copied to ") + to.getPath();
    }

    /** The tests' folder for a kind's Browse (null asks). */
    static java.util.function.Function<String, File> folderFor;

    /**
     * Move downloaded media files to: its box, then (ticked) Images, Videos and Music each with a box,
     * its folder and Browse…, Move now and Move automatically.
     */
    static JPanel movePanel(final Pulsekit app, final JPanel body) {
        DownloadMove.load();
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setName("downloads-move");
        panel.setAlignmentX(0.0f);
        panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        final javax.swing.JCheckBox on = new javax.swing.JCheckBox(DownloadMove.TITLE, DownloadMove.on);
        on.setName("downloads-move-on");
        on.setAlignmentX(0.0f);
        on.addActionListener(e -> {
            DownloadMove.on = on.isSelected();
            DownloadMove.save();
            fill(app, body);
        });
        panel.add(on);
        if (!DownloadMove.on) return panel;
        folderRows(app, panel);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        buttons.setAlignmentX(0.0f);
        final JButton now = new JButton(DownloadMove.MOVE_NOW);
        now.setName("downloads-move-now");
        now.setEnabled(!moving.get());
        // What Move now is doing: the file and how many of them (while it moves), then its result.
        final JLabel status = new JLabel(moveStatus);
        status.setName("downloads-move-status");
        final javax.swing.JProgressBar bar = new javax.swing.JProgressBar(0, 1000);
        bar.setName("downloads-move-progress");
        bar.setVisible(moving.get());
        statusView = status;
        barView = bar;
        now.addActionListener(e -> {
            if (moving.get()) return;
            now.setEnabled(false);
            new Thread(() -> {
                final String said = moveNow();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    app.setNow(said);
                    fill(app, body);
                    askClear(app, body);
                });
            }, "move-downloads").start();
        });
        buttons.add(now);
        final javax.swing.JCheckBox auto = new javax.swing.JCheckBox(DownloadMove.AUTO, DownloadMove.auto);
        auto.setName("downloads-move-auto");
        auto.addActionListener(e -> {
            DownloadMove.auto = auto.isSelected();
            DownloadMove.save();
        });
        buttons.add(auto);
        panel.add(buttons);
        // Also other programs' files: their runs listed too (from now on), so Move now and Move automatically take their files.
        final javax.swing.JCheckBox others = new javax.swing.JCheckBox(DownloadMove.OTHERS, DownloadMove.others);
        others.setName("downloads-move-others");
        others.setToolTipText(DownloadMove.OTHERS_NOTE);
        others.setAlignmentX(0.0f);
        others.addActionListener(e -> {
            DownloadMove.others = others.isSelected();
            DownloadMove.save();
            if (others.isSelected()) app.setNow(DownloadMove.OTHERS_NOTE);
        });
        panel.add(others);
        JPanel progress = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        progress.setAlignmentX(0.0f);
        progress.add(status);
        progress.add(bar);
        panel.add(progress);
        return panel;
    }

    /**
     * Images, Videos and Music: each a box, its folder and Browse… (Downloads > Finished, Settings'
     * Default move folders, the Media browser's question when none is picked).
     */
    static void folderRows(final java.awt.Component app, JPanel panel) {
        SogniDownloads.init(PromptDb.dir());
        DownloadMove.load();
        for (int i = 0; i < DownloadMove.KINDS.length; i++) {
            final String kind = DownloadMove.KINDS[i];
            JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            row.setAlignmentX(0.0f);
            final javax.swing.JCheckBox box = new javax.swing.JCheckBox(DownloadMove.NAMES[i], DownloadMove.isTicked(kind));
            box.setName("downloads-move-kind:" + kind);
            box.addActionListener(e -> {
                DownloadMove.ticked.put(kind, box.isSelected());
                DownloadMove.save();
            });
            row.add(box);
            final JLabel where = new JLabel(DownloadMove.labelOf(kind));
            where.setName("downloads-move-folder:" + kind);
            where.setToolTipText(DownloadMove.folderOf(kind));
            JButton browse = new JButton("Browse\u2026");
            browse.setName("downloads-move-browse:" + kind);
            browse.addActionListener(e -> {
                File dir;
                if (folderFor != null) dir = folderFor.apply(kind);
                else {
                    String now = DownloadMove.folderOf(kind);
                    javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(now.length() > 0 ? new File(now) : null);
                    chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
                    chooser.setDialogTitle(DownloadMove.NAMES[java.util.Arrays.asList(DownloadMove.KINDS).indexOf(kind)] + " go to");
                    dir = chooser.showOpenDialog(app) == javax.swing.JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
                }
                if (dir == null) return;
                DownloadMove.setFolder(kind, dir.getAbsolutePath(), dir.getName());
                // A folder picked: its type ticked.
                DownloadMove.ticked.put(kind, true);
                DownloadMove.save();
                box.setSelected(true);
                where.setText(DownloadMove.labelOf(kind));
                where.setToolTipText(dir.getAbsolutePath());
            });
            row.add(where);
            row.add(browse);
            panel.add(row);
            // Its subfolder under the folder, with % codes (%ddmmyyyy/pics), made when missing.
            JPanel subRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            subRow.setAlignmentX(0.0f);
            subRow.add(new JLabel("      Subfolder"));
            final javax.swing.JTextField subField = new javax.swing.JTextField(DownloadMove.subOf(kind), 22);
            subField.setName("downloads-move-sub:" + kind);
            subField.setToolTipText(DownloadMove.SUB_HINT + ". " + DownloadMove.SUB_NOTE);
            subField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void kept() {
                    DownloadMove.setSub(kind, subField.getText());
                }

                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    kept();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    kept();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    kept();
                }
            });
            subRow.add(subField);
            panel.add(subRow);
        }
        JLabel note = new JLabel("<html>" + DownloadMove.SUB_NOTE + "</html>");
        note.setName("downloads-move-sub-note");
        note.setFont(note.getFont().deriveFont(11f));
        note.setAlignmentX(0.0f);
        panel.add(note);
    }

    /** The kind's folder for a move now: with its subfolder (the % codes filled in) when one is set; made by the move. */
    static File moveFolder(String kind) {
        return moveFolder(kind, "", null);
    }

    /**
     * As above, with a run's Destination subfolder after the kind's own (its group, category and tag codes
     * filled in from the file's choices).
     */
    static File moveFolder(String kind, String runSub, DownloadMove.Choices ch) {
        final File base = new File(DownloadMove.folderOf(kind));
        String sub = DownloadMove.subFor(kind, runSub, ch, p -> new File(base, p).exists());
        return sub.length() == 0 ? base : new File(base, sub);
    }

    /** A download's choices (its groups, category and tags), for its run's Destination subfolder codes. */
    static DownloadMove.Choices choices(SogniDownloads.Item item) {
        File f = new File(item.where);
        FileGroups.load(PromptDb.dir());
        MediaTags.load(PromptDb.dir());
        return DownloadMove.choicesOf(f.getName(), f.length());
    }

    /** Move now going on (one at a time), what it says and how far it is (0-1000), and the views showing it. */
    static final java.util.concurrent.atomic.AtomicBoolean moving = new java.util.concurrent.atomic.AtomicBoolean();
    static volatile String moveStatus = "";
    static volatile int moveProgress;
    private static JLabel statusView;
    private static javax.swing.JProgressBar barView;

    private static void showMove() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (statusView != null) statusView.setText(moveStatus);
            if (barView != null) {
                barView.setValue(moveProgress);
                barView.setVisible(moving.get());
            }
        });
    }

    /** The finished downloads of the ticked kinds moved out of Download into their folders, saying which file and how many. */
    static String moveNow() {
        if (!moving.compareAndSet(false, true)) return "Moving already";
        String result = "";
        lastMoved = 0;
        try {
            DownloadMove.load();
            java.util.List<Object[]> due = DownloadMove.due();
            int moved = 0, failed = 0;
            String problem = null;
            // Each kind's folder (its subfolder's % codes filled in) once for the whole move.
            java.util.Map<String, File> into = new java.util.HashMap<String, File>();
            for (int i = 0; i < due.size(); i++) {
                SogniDownloads.Item item = (SogniDownloads.Item) due.get(i)[1];
                moveStatus = "Moving " + (i + 1) + " of " + due.size() + ": " + item.name;
                moveProgress = i * 1000 / due.size();
                showMove();
                // Gone (deleted once zipped, --zip_delete): left out.
                if (!new File(item.where).isFile()) continue;
                // The run's Destination subfolder after the kind's (one folder for each filled-in pattern: %n counts once).
                SogniDownloads.Run run = (SogniDownloads.Run) due.get(i)[0];
                DownloadMove.Choices ch = run.subfolder.length() > 0 && DownloadMove.needsChoices(run.subfolder) ? choices(item) : null;
                String key = item.kind() + "\n" + DownloadMove.fillChoices(run.subfolder, ch);
                if (!into.containsKey(key)) into.put(key, moveFolder(item.kind(), run.subfolder, ch));
                File dir = into.get(key);
                if (!dir.isDirectory()) dir.mkdirs();
                String said = put(run, item, dir, true);
                if (said.startsWith("Moved to ")) moved++;
                else {
                    failed++;
                    if (problem == null) problem = said;
                }
            }
            lastMoved = moved;
            DownloadMove.afterMove(moved);
            result = DownloadMove.said(moved, failed, problem);
            return result;
        } finally {
            moving.set(false);
            moveStatus = result;
            moveProgress = 0;
            showMove();
        }
    }

    /** How many files Move now moved last. */
    static volatile int lastMoved;

    /**
     * Move now done by hand, something moved: the runs moved whole off the list? (Move automatically
     * asks nothing.) What was answered, for the tests: true when cleared.
     */
    static boolean askClear(Pulsekit app, JPanel body) {
        if (lastMoved == 0) return false;
        int runs = SogniDownloads.movedRuns();
        if (runs == 0) return false;
        Object[] options = {"Clear", "Keep"};
        if (JOptionPane.showOptionDialog(app, DownloadMove.clearAsk(runs), DownloadMove.CLEAR_TITLE,
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) != 0) return false;
        app.setNow(SogniDownloads.clearMoved() + " off the list (the files stay)");
        if (body != null) fill(app, body);
        return true;
    }

    /**
     * The Media browser's File > Move defined media types: the folder's pictures, videos and music whose
     * type is ticked with a folder moved there, the oldest first; the Downloads list and the playlists
     * follow them.
     */
    static String moveToDefaults(File dir) {
        if (!moving.compareAndSet(false, true)) return "Moving already";
        moveStatus = "";
        moveProgress = 0;
        try {
            DownloadMove.load();
            java.util.List<File> files = new java.util.ArrayList<File>();
            File[] all = dir.listFiles();
            for (int i = 0; all != null && i < all.length; i++) {
                File f = all[i];
                String to = f.isFile() && !f.isHidden() ? DownloadMove.defaultFor(f.getName()) : "";
                // A file in its type's folder already stays.
                if (to.length() > 0 && !new File(to).getAbsoluteFile().equals(dir.getAbsoluteFile())) files.add(f);
            }
            java.util.Collections.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            int moved = 0, failed = 0;
            String problem = null;
            java.util.Map<String, File> into = new java.util.HashMap<String, File>();
            for (int i = 0; i < files.size(); i++) {
                File f = files.get(i);
                moveStatus = "Moving " + (i + 1) + " of " + files.size() + ": " + f.getName();
                moveProgress = i * 1000 / files.size();
                showMove();
                String kind = DownloadMove.kindOf(f.getName());
                if (!into.containsKey(kind)) into.put(kind, moveFolder(kind));
                File folder = into.get(kind);
                String stem = f.getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
                String ext = f.getName().substring(stem.length());
                File to = new File(folder, f.getName());
                for (int n = 1; to.exists(); n++) to = new File(folder, stem + " (" + n + ")" + ext);
                try {
                    if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("could not make " + folder.getPath());
                    Files.move(f.toPath(), to.toPath());
                } catch (Exception ex) {
                    failed++;
                    if (problem == null) problem = f.getName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
                    continue;
                }
                moved++;
                java.util.Set<String> olds = java.util.Collections.singleton(f.getAbsolutePath());
                SogniDownloads.Item item = SogniDownloads.inDownload(olds);
                if (item != null) SogniDownloads.movedAway(item, to.getName(), to.getAbsolutePath(), to.getAbsolutePath());
                Playlist.repoint(PromptDb.dir(), olds, to.getAbsolutePath());
            }
            DownloadMove.afterMove(moved);
            moveStatus = DownloadMove.defaultsSaid(moved, failed, problem);
            return moveStatus;
        } finally {
            moving.set(false);
            moveProgress = 0;
            showMove();
        }
    }

    /** A run finished: with Move automatically, its media go to their folders. */
    static void finished(Pulsekit app) {
        if (!DownloadMove.automatic()) return;
        // The run's zip still being made (--zip; --zip_delete deletes what it zipped): the move waits for it.
        if (RunZip.current() != null || DownloadMove.zipping.get() > 0) {
            DownloadMove.waitingForZip = true;
            return;
        }
        String said = moveNow();
        if (said.startsWith("Moved")) app.setNow(said);
    }

    /** The run's zip saved: the move it held up goes now. */
    static void zipSaved(Pulsekit app) {
        if (!DownloadMove.waitingForZip) return;
        DownloadMove.waitingForZip = false;
        finished(app);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
