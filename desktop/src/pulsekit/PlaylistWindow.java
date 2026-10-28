package pulsekit;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * File > Playlist viewer/editor (and PlayList.java's run, a .pls run from PyJav, Import .pls): a
 * playlist's files in two tabs. Editor: each file with its thumbnail and what it is; ▲ ▼ move it,
 * ✕ takes it out, its menu (⋮, or a right click) has Start viewer; Add from DB and Add files add
 * more. Viewer: Repeat, Auto mode, Autoplay video, how long a picture shows, and Start viewer. The
 * viewer shows one file full size: ◀ ▶ the one before and after, a held press plays or pauses a
 * video (a picture: holds the Auto mode there), Play / Pause / Stop, Mute, volume, speed and zoom;
 * the choices are kept (Playlist.Settings). Every change to the list is saved as its .pls.
 */
final class PlaylistWindow {
    private PlaylistWindow() {}

    /** The window and the viewer shown last, and the item menu, for the tests. */
    static JDialog last;
    static JDialog lastViewer;
    static JPopupMenu lastMenu;
    /** The list the window shows. */
    static Playlist shown;
    /** The viewer's item and state, for the tests. */
    static int viewerAt = -1;
    static String viewerNote = "";

    /** The window on the playlist kept by that name (the one open last when null). */
    static void open(final Pulsekit app, String name) {
        final File dir = PromptDb.dir();
        final Playlist.Settings s = Playlist.settings(dir);
        String n = name == null || name.trim().length() == 0 ? s.last : Playlist.clean(name);
        shown = Playlist.load(dir, n);
        s.last = shown.name;
        Playlist.saveSettings(dir, s);
        final JDialog d = new JDialog(app, "Playlist · " + shown.name, true);
        d.setName("playlist-window");
        final JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> fill(app, d, body, fill[0]);
        fill[0].run();
        d.setContentPane(body);
        d.pack();
        d.setSize(new Dimension(Math.max(700, d.getWidth()), Math.max(560, d.getHeight())));
        d.setLocationRelativeTo(app);
        last = d;
        d.setVisible(true);
    }

    /** The playlist kept by `p.name` saved; the status line on failure. */
    static void save(Pulsekit app, Playlist p) {
        try {
            Playlist.save(PromptDb.dir(), p);
        } catch (Exception ex) {
            app.setNow("Could not save " + p.name + ": " + ex.getMessage());
        }
    }

    private static void fill(final Pulsekit app, final JDialog d, final JPanel body, final Runnable again) {
        body.removeAll();
        final File dir = PromptDb.dir();
        final Playlist p = shown;
        d.setTitle("Playlist · " + p.name);
        // The playlists, and New, Rename, Delete, Import .pls, Merge .pls, Save, Save as.
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        final JComboBox<String> pick = new JComboBox<String>(Playlist.names(dir).toArray(new String[0]));
        pick.setName("playlist-pick");
        pick.setSelectedItem(p.name);
        pick.addActionListener(e -> {
            String to = (String) pick.getSelectedItem();
            if (to == null || to.equals(p.name)) return;
            switchTo(dir, to);
            SwingUtilities.invokeLater(again);
        });
        top.add(new JLabel("Playlist"));
        top.add(pick);
        JButton make = new JButton("New…");
        make.setName("playlist-new");
        make.addActionListener(e -> {
            String typed = ask(d, "New playlist", "");
            if (typed == null) return;
            switchTo(dir, typed);
            save(app, shown);
            again.run();
        });
        top.add(make);
        JButton rename = new JButton("Rename…");
        rename.setName("playlist-rename");
        rename.addActionListener(e -> {
            String typed = ask(d, "Rename " + p.name, p.name);
            if (typed == null || typed.equals(p.name)) return;
            if (Playlist.file(dir, typed).isFile()) {
                app.setNow("There is a playlist " + typed + " already");
                return;
            }
            File old = Playlist.file(dir, p.name);
            p.name = typed;
            save(app, p);
            if (!old.equals(Playlist.file(dir, typed))) old.delete();
            switchTo(dir, typed);
            again.run();
        });
        top.add(rename);
        JButton delete = new JButton("Delete…");
        delete.setName("playlist-delete");
        delete.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(d, "Delete the playlist " + p.name + "? Its files stay where they are.", "Delete playlist", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            Playlist.file(dir, p.name).delete();
            app.setNow("Playlist " + p.name + " deleted");
            switchTo(dir, Playlist.DEFAULT);
            again.run();
        });
        top.add(delete);
        // A second row: Save, Save as…, Import .pls, Merge .pls.
        JPanel files = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton imp = new JButton("Import .pls…");
        imp.setName("playlist-import");
        imp.addActionListener(e -> {
            File[] picked = choosePls(d, false);
            if (picked.length == 0) return;
            String name = importPls(app, picked[0]);
            if (name == null) return;
            switchTo(dir, name);
            again.run();
        });
        files.add(imp);
        JButton merge = new JButton("Merge .pls…");
        merge.setName("playlist-merge");
        merge.setToolTipText("Two or three playlists as one (PlayList.java)");
        merge.addActionListener(e -> {
            File[] picked = choosePls(d, true);
            if (picked.length == 0) return;
            String name = runPlayList(app, picked);
            if (name == null) return;
            switchTo(dir, name);
            again.run();
        });
        files.add(merge);
        // Save: the .pls where it was saved last (Save as… when it has not been); Save as…: a place picked.
        JButton saveIt = new JButton(Playlist.SAVE);
        saveIt.setName("playlist-save");
        saveIt.setToolTipText("The playlist as a .pls where it was saved last (the editor keeps it in Pulsekit as you go)");
        saveIt.addActionListener(e -> {
            String at = Playlist.savedAt(dir, p.name);
            if (at != null && new File(at).getParentFile() != null && new File(at).getParentFile().isDirectory()) savePls(app, p, new File(at));
            else saveAs(app, d, p);
        });
        files.add(saveIt, 0);
        JButton export = new JButton(Playlist.SAVE_AS);
        export.setName("playlist-export");
        export.addActionListener(e -> saveAs(app, d, p));
        files.add(export, 1);
        JPanel bars = new JPanel(new java.awt.GridLayout(2, 1, 0, 4));
        bars.add(top);
        bars.add(files);
        body.add(bars, BorderLayout.NORTH);
        final JTabbedPane tabs = new JTabbedPane();
        tabs.setName("playlist-tabs");
        tabs.addTab("Editor", editor(app, d, p, again));
        tabs.addTab("Viewer", viewerTab(app, d, p));
        body.add(tabs, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.setName("playlist-close");
        close.addActionListener(e -> d.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        south.add(close);
        body.add(south, BorderLayout.SOUTH);
        body.revalidate();
        body.repaint();
    }

    /** The test's answer to Save as…'s chooser (null: the chooser). */
    static File saveAsAnswer;

    /** Save as…: a .pls file picked; it is where Save writes from then on. */
    static void saveAs(Pulsekit app, java.awt.Component d, Playlist p) {
        File to = saveAsAnswer;
        if (to == null) {
            String at = Playlist.savedAt(PromptDb.dir(), p.name);
            JFileChooser chooser = new JFileChooser(at == null ? null : new File(at).getParentFile());
            chooser.setSelectedFile(at == null ? new File(p.name + Playlist.EXT) : new File(at));
            if (chooser.showSaveDialog(d) != JFileChooser.APPROVE_OPTION) return;
            to = chooser.getSelectedFile();
        }
        if (!to.getName().toLowerCase().endsWith(Playlist.EXT)) to = new File(to.getParentFile(), to.getName() + Playlist.EXT);
        if (savePls(app, p, to)) Playlist.setSavedAt(PromptDb.dir(), p.name, to.getAbsolutePath());
    }

    static boolean savePls(Pulsekit app, Playlist p, File to) {
        try {
            Files.write(to.toPath(), p.toPls().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            app.setNow("Saved " + to.getPath());
            return true;
        } catch (Exception ex) {
            app.setNow("Could not save " + to.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private static void switchTo(File dir, String name) {
        shown = Playlist.load(dir, name);
        Playlist.Settings s = Playlist.settings(dir);
        s.last = shown.name;
        Playlist.saveSettings(dir, s);
    }

    private static String ask(java.awt.Component at, String title, String now) {
        Object typed = JOptionPane.showInputDialog(at, "Playlist name", title, JOptionPane.PLAIN_MESSAGE, null, null, now);
        String n = typed == null ? "" : Playlist.clean(typed.toString());
        return n.length() == 0 ? null : n;
    }

    /** The test's pick in place of the file chooser (null: the chooser). */
    static File[] choose;

    private static File[] choosePls(java.awt.Component at, boolean several) {
        if (choose != null) {
            File[] c = choose;
            choose = null;
            return c;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(several ? "Two or three playlists to merge" : "A playlist (.pls)");
        chooser.setMultiSelectionEnabled(several);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Playlists (.pls)", "pls"));
        if (chooser.showOpenDialog(at) != JFileChooser.APPROVE_OPTION) return new File[0];
        return several ? chooser.getSelectedFiles() : new File[] {chooser.getSelectedFile()};
    }

    /** A .pls copied in with the playlists (by its name; " (2)" when that is taken); its name, or null. */
    static String importPls(Pulsekit app, File pls) {
        try {
            Playlist p = Playlist.parse(new String(Files.readAllBytes(pls.toPath()), java.nio.charset.StandardCharsets.UTF_8), pls.getName());
            String base = p.name;
            for (int n = 2; Playlist.file(PromptDb.dir(), p.name).isFile(); n++) p.name = base + " (" + n + ")";
            Playlist.save(PromptDb.dir(), p);
            app.setNow("Imported " + pls.getName() + " as " + p.name + " (" + p.items.size() + (p.items.size() == 1 ? " file)" : " files)"));
            return p.name;
        } catch (Exception ex) {
            app.setNow("Could not import " + pls.getName() + ": " + ex.getMessage());
            return null;
        }
    }

    /** PlayList.java run on the files (a merge for two or three); what it wrote kept with the playlists; its name, or null. */
    static String runPlayList(Pulsekit app, File[] pls) {
        List<String> argv = new ArrayList<String>();
        for (File f : pls) argv.add(f.getAbsolutePath());
        try {
            String src = new String(ProgramFiles.read("Java", "PlayList.java"), java.nio.charset.StandardCharsets.UTF_8);
            JavaRun.Result r = JavaRun.run("PlayList.java", src, null, argv);
            return kept(app, r);
        } catch (Exception ex) {
            app.setNow("PlayList.java could not run: " + ex.getMessage());
            return null;
        }
    }

    /**
     * PlayList.java's run: the playlist it wrote kept with the playlists (by its name; replaced when it
     * is there), and its name; "Default" when it was run without one; null when it failed.
     */
    static String kept(Pulsekit app, JavaRun.Result r) {
        if (r == null) return null;
        List<String> names = new ArrayList<String>();
        List<byte[]> bytes = new ArrayList<byte[]>();
        for (JavaRun.FileOut f : r.files) {
            names.add(f.name);
            bytes.add(f.bytes);
        }
        return kept(app, r.log, r.code, names, bytes);
    }

    /** As above, for PyJav's run (its result as PythonRun has it). */
    static String kept(Pulsekit app, PythonRun.Result r) {
        if (r == null) return null;
        List<String> names = new ArrayList<String>();
        List<byte[]> bytes = new ArrayList<byte[]>();
        for (PythonRun.FileOut f : r.files) {
            names.add(f.name);
            bytes.add(f.bytes);
        }
        return kept(app, r.log, r.code, names, bytes);
    }

    private static String kept(Pulsekit app, String log, int code, List<String> names, List<byte[]> bytes) {
        // --nogui: the outfile is a file the run made, not a playlist to keep and open.
        if (!Playlist.announced(log)) return null;
        for (int i = 0; i < names.size(); i++) {
            if (!names.get(i).toLowerCase().endsWith(Playlist.EXT)) continue;
            Playlist p = Playlist.parse(new String(bytes.get(i), java.nio.charset.StandardCharsets.UTF_8), names.get(i));
            save(app, p);
            app.setNow("Playlist " + p.name + ": " + p.items.size() + (p.items.size() == 1 ? " file" : " files"));
            return p.name;
        }
        // Run without a playlist: the one used last ("" opens it).
        if (code == 0 && log != null && log.contains("Playlist: " + Playlist.DEFAULT)) return "";
        app.setNow(log == null ? "PlayList.java failed" : lastLine(log));
        return null;
    }

    private static String lastLine(String log) {
        String[] lines = log.trim().split("\n");
        return lines[lines.length - 1];
    }

    // ------------------------------------------------------------ Add to playlist (the thumbnails' menus)

    /** "Add to playlist…": the playlists (Default first), then New playlist…; the file goes at the end of the one picked. */
    static javax.swing.JMenu addMenu(final Pulsekit app, final String file, final String title) {
        javax.swing.JMenu m = new javax.swing.JMenu(Playlist.ADD);
        m.setName("add-to-playlist");
        // The playlist used last for this kind of file first; each with the kinds it has.
        String[][] rows = Playlist.menuFor(PromptDb.dir(), title);
        for (int i = 0; i < rows[1].length - 1; i++) {
            final String n = rows[1][i];
            JMenuItem item = new JMenuItem(rows[0][i]);
            item.setName("playlist-add:" + n);
            item.addActionListener(e -> confirmAdd(app, n, file, title));
            m.add(item);
        }
        JMenuItem make = new JMenuItem(Playlist.NEW);
        make.setName("playlist-add-new");
        make.addActionListener(e -> {
            String typed = ask(app, Playlist.NEW, "");
            if (typed != null) confirmAdd(app, typed, file, title);
        });
        m.add(make);
        return m;
    }

    /** Add to playlist as a question of its own (Video ready): the playlists, then New playlist…. */
    static void askAdd(Pulsekit app, String file, String title) {
        // The playlist used last for this kind of file first; each with the kinds it has.
        String[][] menu = Playlist.menuFor(PromptDb.dir(), title);
        Object picked = JOptionPane.showInputDialog(app, "Playlist for " + title, Playlist.ADD.replace("\u2026", ""), JOptionPane.PLAIN_MESSAGE, null, menu[0], menu[0][0]);
        if (picked == null) return;
        String name = picked.toString();
        for (int i = 0; i < menu[0].length; i++) if (menu[0][i].equals(name)) name = menu[1][i];
        if (Playlist.NEW.equals(name)) {
            name = ask(app, Playlist.NEW, "");
            if (name == null) return;
        }
        confirmAdd(app, name, file, title);
    }

    /** The file into the named list; when it is there already, asked first ("Add it a second time?"). */
    static void confirmAdd(Pulsekit app, String list, String file, String title) {
        File dir = PromptDb.dir();
        if (!Playlist.inList(dir, list, file)) {
            app.setNow(Playlist.addTo(dir, list, file, title));
            return;
        }
        if (askAgain(app, Playlist.againQuestion(title, Playlist.clean(list).length() == 0 ? Playlist.DEFAULT : Playlist.clean(list)))) {
            app.setNow(Playlist.addTo(dir, list, file, title, true));
        }
    }

    /** "… is in … already. Add it a second time?": true for Add again. */
    static boolean askAgain(Pulsekit app, String question) {
        Object[] options = {"Add again", "Cancel"};
        return JOptionPane.showOptionDialog(app, question, "In the playlist already", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == 0;
    }

    /** "Add to default playlist": at the end of Default. */
    static JMenuItem addDefaultItem(final Pulsekit app, final String file, final String title) {
        JMenuItem item = new JMenuItem(Playlist.ADD_DEFAULT);
        item.setName("add-to-default-playlist");
        item.addActionListener(e -> confirmAdd(app, Playlist.DEFAULT, file, title));
        return item;
    }

    // ------------------------------------------------------------ Editor

    private static JScrollPane editor(final Pulsekit app, final JDialog d, final Playlist p, final Runnable again) {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setName("playlist-list");
        if (p.items.isEmpty()) list.add(new JLabel("No files yet: Add from DB or Add files below, or Add to playlist on a thumbnail."));
        final List<JLabel> thumbs = new ArrayList<JLabel>();
        for (int i = 0; i < p.items.size(); i++) {
            final int at = i;
            final Playlist.Item it = p.items.get(i);
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setName("playlist-item:" + i);
            row.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
            JLabel thumb = new JLabel(kindMark(it), JLabel.CENTER);
            thumb.setPreferredSize(new Dimension(72, 54));
            thumb.setOpaque(true);
            thumb.setBackground(new Color(0x1B1D1F));
            thumb.setForeground(new Color(0xECEBE6));
            thumb.setName("playlist-thumb:" + i);
            thumbs.add(thumb);
            row.add(thumb, BorderLayout.WEST);
            final JLabel text = new JLabel("<html><b>" + (i + 1) + ". " + esc(it.title) + "</b><br>" + esc(Playlist.info(it, -1)) + "</html>");
            text.setName("playlist-info:" + i);
            row.add(text, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            JButton up = small("▲", "playlist-up:" + i, "Up");
            up.setEnabled(i > 0);
            up.addActionListener(e -> {
                if (p.move(at, at - 1)) save(app, p);
                again.run();
            });
            JButton down = small("▼", "playlist-down:" + i, "Down");
            down.setEnabled(i < p.items.size() - 1);
            down.addActionListener(e -> {
                if (p.move(at, at + 1)) save(app, p);
                again.run();
            });
            JButton remove = small("✕", "playlist-remove:" + i, "Take it out of the list (the file stays)");
            remove.addActionListener(e -> {
                if (p.remove(at)) save(app, p);
                app.setNow(it.title + " taken out of " + p.name);
                again.run();
            });
            final JButton more = small("⋮", "playlist-menu:" + i, "Start viewer, move, remove");
            more.addActionListener(e -> menu(app, d, p, at, more, again));
            buttons.add(up);
            buttons.add(down);
            buttons.add(remove);
            buttons.add(more);
            row.add(buttons, BorderLayout.EAST);
            GroupMenus.onRightClick(row, () -> menu(app, d, p, at, row, again));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            list.add(row);
            // Its size beside it once read (with the thumbnail).
            thumb.putClientProperty("info", text);
        }
        // The thumbnails and sizes, read off the screen's thread.
        final List<Playlist.Item> items = new ArrayList<Playlist.Item>(p.items);
        new Thread(() -> {
            for (int i = 0; i < items.size() && i < thumbs.size(); i++) {
                final Playlist.Item it = items.get(i);
                final File f = local(it);
                final long size = f == null ? -1 : f.length();
                // No thumbnail for a type General settings leaves out (Show images, Show videos).
                final ImageIcon icon = AppSettings.shownItems(java.util.Collections.singletonList(it)).isEmpty() ? null : thumb(it, f, 72, 54);
                final JLabel l = thumbs.get(i);
                final int n = i;
                SwingUtilities.invokeLater(() -> {
                    if (icon != null) {
                        l.setText("");
                        l.setIcon(icon);
                    }
                    JLabel text = (JLabel) l.getClientProperty("info");
                    if (text != null) text.setText("<html><b>" + (n + 1) + ". " + esc(it.title) + "</b><br>" + esc(Playlist.info(it, size)) + "</html>");
                });
            }
        }, "pulsekit-playlist-thumbs").start();
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(list, BorderLayout.NORTH);
        JPanel adds = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton fromDb = new JButton("Add from DB…");
        fromDb.setName("playlist-add-db");
        fromDb.addActionListener(e -> app.promptDb.browse(null, (picked, copy) -> {
            PromptVault.StoredFile f = stored(picked, copy.length());
            if (f == null) {
                app.setNow("Could not find " + picked + " in the prompt library");
                return;
            }
            String file = Playlist.dbAddress(f);
            // In the list already: asked before it goes in a second time.
            if (p.has(file)) {
                if (!askAgain(app, Playlist.againQuestion(f.name, p.name))) return;
                p.addAgain(file, f.name);
            } else {
                p.add(file, f.name);
            }
            app.setNow(f.name + " added to " + p.name);
            save(app, p);
            again.run();
        }));
        JButton files = new JButton("Add files…");
        files.setName("playlist-add-files");
        files.addActionListener(e -> {
            File[] picked = chooseMedia(d);
            int added = 0;
            List<File> twice = new ArrayList<File>();
            for (File f : picked) {
                if (p.add(f.getAbsolutePath(), f.getName())) added++;
                else twice.add(f);
            }
            if (picked.length == 0) return;
            // Those in the list already: asked before they go in a second time.
            if (!twice.isEmpty() && askAgain(app, twice.size() == 1 ? Playlist.againQuestion(twice.get(0).getName(), p.name)
                : twice.size() + " of the files are in " + p.name + " already. Add them a second time?")) {
                for (File f : twice) p.addAgain(f.getAbsolutePath(), f.getName());
                added += twice.size();
            }
            save(app, p);
            app.setNow(added + (added == 1 ? " file" : " files") + " added to " + p.name);
            again.run();
        });
        adds.add(fromDb);
        adds.add(files);
        wrap.add(adds, BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(wrap);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setName("playlist-editor");
        return scroll;
    }

    /** The test's pick in place of Add files' chooser (null: the chooser). */
    static File[] chooseFiles;

    private static File[] chooseMedia(java.awt.Component at) {
        if (chooseFiles != null) {
            File[] c = chooseFiles;
            chooseFiles = null;
            return c;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Pictures, videos or sounds");
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(at) != JFileChooser.APPROVE_OPTION) return new File[0];
        return chooser.getSelectedFiles();
    }

    /** The item's menu: Start viewer (from it), Move up / down, to the top / bottom, Remove. */
    private static void menu(final Pulsekit app, final JDialog d, final Playlist p, final int at, java.awt.Component where, final Runnable again) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem start = new JMenuItem("Start viewer");
        start.setName("playlist-item-start");
        start.addActionListener(e -> viewer(app, d, p, at));
        m.add(start);
        String[][] moves = {{"Move up", "playlist-item-up"}, {"Move down", "playlist-item-down"}, {"Move to the top", "playlist-item-top"}, {"Move to the bottom", "playlist-item-bottom"}};
        int[] to = {at - 1, at + 1, 0, p.items.size() - 1};
        for (int k = 0; k < moves.length; k++) {
            final int dest = to[k];
            JMenuItem item = new JMenuItem(moves[k][0]);
            item.setName(moves[k][1]);
            item.setEnabled(dest >= 0 && dest < p.items.size() && dest != at);
            item.addActionListener(e -> {
                if (p.move(at, dest)) save(app, p);
                again.run();
            });
            m.add(item);
        }
        JMenuItem remove = new JMenuItem("Remove from the list");
        remove.setName("playlist-item-remove");
        remove.addActionListener(e -> {
            String title = p.items.get(at).title;
            if (p.remove(at)) save(app, p);
            app.setNow(title + " taken out of " + p.name);
            again.run();
        });
        m.add(remove);
        lastMenu = m;
        MenuCancel.add(m);
        if (where != null && where.isShowing()) m.show(where, 0, where.getHeight());
    }

    private static JButton small(String text, String name, String tip) {
        JButton b = new JButton(text);
        b.setName(name);
        b.setToolTipText(tip);
        b.setMargin(new java.awt.Insets(2, 6, 2, 6));
        return b;
    }

    private static String kindMark(Playlist.Item it) {
        int k = it.kind();
        return k == MediaDir.VIDEO ? "▶ video" : k == MediaDir.SOUND ? "♪ sound" : k == MediaDir.PICTURE ? "picture" : "file";
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** A library file by its name and size (Browse DB hands on a copy). */
    static PromptVault.StoredFile stored(String name, long size) {
        PromptVault.StoredFile byName = null;
        for (PromptVault.StoredFile f : PromptDb.allFiles(null)) {
            if (!f.name.equals(name)) continue;
            if (f.size == size) return f;
            if (byName == null) byName = f;
        }
        return byName;
    }

    /** The item as a file here: its path, or a library file written out (kept for the session); null when it is not there. */
    static File local(Playlist.Item it) {
        if (Playlist.isDb(it.file)) {
            try {
                byte[] bytes = Playlist.dbBytes(PromptDb.vault(), it.file);
                if (bytes == null) return null;
                File cache = new File(new File(PromptDb.dir(), "playlist-cache"), Playlist.dbPlace(it.file)[1] + "-" + Playlist.shortName(it.file));
                if (cache.isFile() && cache.length() == bytes.length) return cache;
                cache.getParentFile().mkdirs();
                Files.write(cache.toPath(), bytes);
                cache.deleteOnExit();
                return cache;
            } catch (Exception ex) {
                return null;
            }
        }
        File f = new File(it.file);
        return f.isFile() ? f : null;
    }

    /** A thumbnail: a picture scaled down, a video's first frame (with VLC); null otherwise. */
    static ImageIcon thumb(Playlist.Item it, File f, int w, int h) {
        if (f == null) return null;
        try {
            BufferedImage img = null;
            int k = it.kind();
            if (k == MediaDir.PICTURE) img = javax.imageio.ImageIO.read(f);
            else if (k == MediaDir.VIDEO && VlcPlayer.available()) {
                byte[] jpeg = VlcPlayer.firstFrame(f, 320);
                if (jpeg != null) img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
            }
            if (img == null) return null;
            double s = Math.min(w / (double) img.getWidth(), h / (double) img.getHeight());
            int tw = Math.max(1, (int) Math.round(img.getWidth() * s));
            int th = Math.max(1, (int) Math.round(img.getHeight() * s));
            return new ImageIcon(img.getScaledInstance(tw, th, java.awt.Image.SCALE_SMOOTH));
        } catch (Exception ex) {
            return null;
        }
    }

    // ------------------------------------------------------------ Viewer tab

    private static JPanel viewerTab(final Pulsekit app, final JDialog d, final Playlist p) {
        final File dir = PromptDb.dir();
        final Playlist.Settings s = Playlist.settings(dir);
        JPanel col = new JPanel(new GridLayout(0, 1, 0, 6));
        col.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        final JCheckBox repeat = new JCheckBox("Repeat: from the end back to the beginning", s.repeat);
        repeat.setName("playlist-repeat");
        final JCheckBox auto = new JCheckBox("Auto mode: the next file shows by itself (else press \u25b6)", s.auto);
        auto.setName("playlist-auto");
        final JCheckBox loop = new JCheckBox(Playlist.LOOP_CAPTION, s.loop);
        loop.setName("playlist-loop");
        final JCheckBox autoplay = new JCheckBox(Playlist.AUTOPLAY_CAPTION.replace("a long press", "holding it"), s.autoplay);
        autoplay.setName("playlist-autoplay");
        final JSpinner seconds = new JSpinner(new SpinnerNumberModel(s.seconds, 1, 600, 1));
        seconds.setName("playlist-seconds");
        // The player's own, as the viewer starts with them (and keeps what is changed there).
        final JSlider volume = new JSlider(0, 100, s.volume);
        volume.setName("playlist-volume");
        final JLabel level = new JLabel(s.volume + "%");
        final JCheckBox mute = new JCheckBox("Mute", s.mute);
        mute.setName("playlist-mute");
        String[] speeds = new String[Playlist.SPEEDS.length];
        for (int i = 0; i < speeds.length; i++) speeds[i] = Playlist.speedText(Playlist.SPEEDS[i]);
        final JComboBox<String> speed = new JComboBox<String>(speeds);
        speed.setName("playlist-speed");
        speed.setSelectedItem(Playlist.speedText(s.speed));
        final float[] zoom = {s.zoom};
        final float[] zoomVideo = {s.zoomVideo};
        final JLabel zoomLevel = new JLabel(MediaDir.zoomLabel(zoom[0]));
        zoomLevel.setName("playlist-zoom");
        final JLabel videoZoomLevel = new JLabel(MediaDir.zoomLabel(zoomVideo[0]));
        videoZoomLevel.setName("playlist-video-zoom");
        final Runnable keep = () -> {
            Playlist.Settings now = Playlist.settings(dir);
            now.repeat = repeat.isSelected();
            now.auto = auto.isSelected();
            now.loop = loop.isSelected();
            now.autoplay = autoplay.isSelected();
            now.seconds = (Integer) seconds.getValue();
            now.volume = volume.getValue();
            now.mute = mute.isSelected();
            now.speed = Playlist.SPEEDS[Math.max(0, speed.getSelectedIndex())];
            now.zoom = zoom[0];
            now.zoomVideo = zoomVideo[0];
            Playlist.saveSettings(dir, now);
            level.setText(now.mute ? "muted" : now.volume + "%");
            zoomLevel.setText(MediaDir.zoomLabel(now.zoom));
            videoZoomLevel.setText(MediaDir.zoomLabel(now.zoomVideo));
        };
        // Loop video and Auto mode go one at a time.
        loop.addActionListener(e -> {
            if (loop.isSelected()) auto.setSelected(false);
            keep.run();
        });
        auto.addActionListener(e -> {
            if (auto.isSelected()) loop.setSelected(false);
            keep.run();
        });
        repeat.addActionListener(e -> keep.run());
        autoplay.addActionListener(e -> keep.run());
        seconds.addChangeListener(e -> keep.run());
        volume.addChangeListener(e -> {
            if (!volume.getValueIsAdjusting()) keep.run();
        });
        mute.addActionListener(e -> keep.run());
        speed.addActionListener(e -> keep.run());
        JButton zoomOut = new JButton("Zoom \u2212");
        zoomOut.setName("playlist-zoom-out");
        zoomOut.addActionListener(e -> {
            zoom[0] = (float) MediaDir.zoom(zoom[0], false);
            keep.run();
        });
        JButton zoomIn = new JButton("Zoom +");
        zoomIn.setName("playlist-zoom-in");
        zoomIn.addActionListener(e -> {
            zoom[0] = (float) MediaDir.zoom(zoom[0], true);
            keep.run();
        });
        JButton fit = new JButton("Fit");
        fit.setName("playlist-fit");
        fit.addActionListener(e -> {
            zoom[0] = 1f;
            keep.run();
        });
        col.add(repeat);
        col.add(auto);
        col.add(loop);
        col.add(autoplay);
        JPanel secs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        secs.add(new JLabel("In Auto mode a picture shows for"));
        secs.add(seconds);
        secs.add(new JLabel("seconds"));
        col.add(secs);
        JPanel sound = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sound.add(new JLabel("Volume"));
        sound.add(volume);
        sound.add(level);
        sound.add(mute);
        col.add(sound);
        JPanel look = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        look.add(new JLabel("Speed"));
        look.add(speed);
        look.add(new JLabel("   Picture zoom"));
        look.add(zoomOut);
        look.add(zoomIn);
        look.add(fit);
        look.add(zoomLevel);
        col.add(look);
        // A video's zoom, kept apart from a picture's.
        JPanel lookVideo = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        lookVideo.add(new JLabel("Video zoom"));
        String[][] vz = {{"Zoom \u2212", "playlist-video-zoom-out"}, {"Zoom +", "playlist-video-zoom-in"}, {"Fit", "playlist-video-fit"}};
        for (int i = 0; i < vz.length; i++) {
            final int which = i;
            JButton b = new JButton(vz[i][0]);
            b.setName(vz[i][1]);
            b.addActionListener(e -> {
                zoomVideo[0] = which == 2 ? 1f : (float) MediaDir.zoom(zoomVideo[0], which == 1);
                keep.run();
            });
            lookVideo.add(b);
        }
        lookVideo.add(videoZoomLevel);
        col.add(lookVideo);
        JButton start = new JButton("Start viewer");
        start.setName("playlist-start");
        start.setEnabled(!p.items.isEmpty());
        start.addActionListener(e -> viewer(app, d, p, 0));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(start);
        col.add(row);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(col, BorderLayout.NORTH);
        wrap.setName("playlist-viewer-tab");
        return wrap;
    }

    // ------------------------------------------------------------ The viewer

    /** A picture full size: zoomed by the wheel and Zoom − / +, dragged when zoomed; a held press runs `held`. */
    static final class Picture extends JPanel {
        BufferedImage image;
        /** A sound's place on its picture (0..1), or -1 for none. */
        double playhead = -1;
        double zoom = 1;
        int panX;
        int panY;
        Runnable held;
        private final javax.swing.Timer hold = new javax.swing.Timer(600, e -> {
            if (this.held != null) this.held.run();
        });

        Picture() {
            this.setBackground(Color.BLACK);
            this.setPreferredSize(new Dimension(720, 440));
            this.hold.setRepeats(false);
            this.addMouseWheelListener(e -> this.zoomTo(MediaDir.zoom(this.zoom, e.getWheelRotation() < 0)));
            java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
                int x;
                int y;
                int pressX;
                int pressY;

                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    this.x = e.getX() - Picture.this.panX;
                    this.y = e.getY() - Picture.this.panY;
                    this.pressX = e.getX();
                    this.pressY = e.getY();
                    if (SwingUtilities.isLeftMouseButton(e)) Picture.this.hold.restart();
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    Picture.this.hold.stop();
                }

                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (Math.abs(e.getX() - this.pressX) > 5 || Math.abs(e.getY() - this.pressY) > 5) Picture.this.hold.stop();
                    if (Picture.this.zoom <= 1) return;
                    Picture.this.panX = e.getX() - this.x;
                    Picture.this.panY = e.getY() - this.y;
                    Picture.this.repaint();
                }
            };
            this.addMouseListener(drag);
            this.addMouseMotionListener(drag);
        }

        void zoomTo(double z) {
            this.zoom = MediaDir.clampZoom(z);
            if (this.zoom == 1) {
                this.panX = 0;
                this.panY = 0;
            }
            this.repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            BufferedImage img = this.image;
            if (img == null) return;
            double s = Math.min(this.getWidth() / (double) img.getWidth(), this.getHeight() / (double) img.getHeight()) * this.zoom;
            int w = (int) Math.round(img.getWidth() * s);
            int h = (int) Math.round(img.getHeight() * s);
            int x0 = (this.getWidth() - w) / 2 + this.panX;
            int y0 = (this.getHeight() - h) / 2 + this.panY;
            g.drawImage(img, x0, y0, w, h, null);
            if (this.playhead >= 0) {
                g.setColor(new Color(0xE0B04A));
                int x = x0 + (int) Math.round(Math.min(1, this.playhead) * w);
                g.fillRect(x - 1, y0, 2, h);
            }
        }
    }

    /** The viewer on the list from item `start`. */
    static void viewer(final Pulsekit app, java.awt.Window owner, final Playlist p, int start) {
        // The types General settings leaves out are not shown (the list itself keeps them).
        if (!AppSettings.showsAll()) {
            Playlist shown = AppSettings.shownPlaylist(p);
            if (shown.items.isEmpty() && !p.items.isEmpty()) {
                app.setNow(AppSettings.nothingShown(p.name));
                return;
            }
            if (shown.items.size() != p.items.size()) {
                viewer(app, owner, shown, AppSettings.shownStart(p, start));
                return;
            }
        }
        if (p.items.isEmpty()) {
            app.setNow(p.name + " has no files yet");
            return;
        }
        final File dir = PromptDb.dir();
        final Playlist.Settings s = Playlist.settings(dir);
        final JDialog v = new JDialog(owner, "Playlist viewer · " + p.name, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        v.setName("playlist-viewer");
        final CardLayout cards = new CardLayout();
        final JPanel stage = new JPanel(cards);
        stage.setPreferredSize(new Dimension(760, 460));
        final Picture picture = new Picture();
        picture.setName("viewer-picture");
        final JLabel message = new JLabel("", JLabel.CENTER);
        message.setName("viewer-message");
        message.setOpaque(true);
        message.setBackground(Color.BLACK);
        message.setForeground(new Color(0xECEBE6));
        final JPanel videoCard = new JPanel(new BorderLayout());
        stage.add(picture, "picture");
        stage.add(message, "message");
        stage.add(videoCard, "video");
        final int[] at = {Math.max(0, Math.min(start, p.items.size() - 1))};
        final MediaPlayer[] player = new MediaPlayer[1];
        final long[] shownAt = new long[1];
        final boolean[] held = new boolean[1];
        final JLabel where = new JLabel();
        where.setName("viewer-where");
        JButton prev = new JButton("◀");
        prev.setName("viewer-prev");
        JButton next = new JButton("▶");
        next.setName("viewer-next");
        // First and Last: the playlist's first and last file.
        JButton first = new JButton("⏮");
        first.setName("viewer-first");
        first.setToolTipText("The first file");
        JButton lastOne = new JButton("⏭");
        lastOne.setName("viewer-last");
        lastOne.setToolTipText("The last file");
        JButton play = new JButton("Play");
        play.setName("viewer-play");
        JButton pause = new JButton("Pause");
        pause.setName("viewer-pause");
        JButton stop = new JButton("Stop");
        stop.setName("viewer-stop");
        final JButton mute = new JButton(s.mute ? "Unmute" : "Mute");
        mute.setName("viewer-mute");
        final JSlider volume = new JSlider(0, 100, s.volume);
        volume.setName("viewer-volume");
        volume.setPreferredSize(new Dimension(110, volume.getPreferredSize().height));
        String[] speeds = new String[Playlist.SPEEDS.length];
        for (int i = 0; i < speeds.length; i++) speeds[i] = Playlist.speedText(Playlist.SPEEDS[i]);
        final JComboBox<String> speed = new JComboBox<String>(speeds);
        speed.setName("viewer-speed");
        speed.setSelectedItem(Playlist.speedText(s.speed));
        JButton zoomOut = new JButton("Zoom −");
        zoomOut.setName("viewer-zoom-out");
        JButton zoomIn = new JButton("Zoom +");
        zoomIn.setName("viewer-zoom-in");
        JButton fit = new JButton("Fit");
        fit.setName("viewer-fit");
        final JLabel zoomLevel = new JLabel(MediaDir.zoomLabel(s.zoom));
        zoomLevel.setName("viewer-zoom");
        picture.zoom = MediaDir.clampZoom(s.zoom);
        // Pictures and videos each keep a zoom of their own (picture.zoom holds the one shown).
        final boolean[] videoShown = new boolean[1];
        // A sound (MIDI, WAV, MP3) plays at its own speed and has no zoom: those controls go while one shows.
        final boolean[] soundShown = new boolean[1];
        final java.util.List<java.awt.Component> lookParts = new java.util.ArrayList<java.awt.Component>();
        final JCheckBox[] loopRef = new JCheckBox[1];
        // A video's place: "00:12 / 01:30".
        final JLabel timeText = new JLabel("");
        timeText.setName("viewer-time");
        final Runnable keep = () -> {
            Playlist.Settings now = Playlist.settings(dir);
            now.volume = volume.getValue();
            now.mute = "Unmute".equals(mute.getText());
            now.speed = Playlist.SPEEDS[Math.max(0, speed.getSelectedIndex())];
            if (videoShown[0]) now.zoomVideo = (float) picture.zoom;
            else now.zoom = (float) picture.zoom;
            Playlist.saveSettings(dir, now);
        };
        final Runnable release = () -> {
            if (player[0] != null) {
                player[0].release();
                player[0] = null;
            }
            videoCard.removeAll();
        };
        final Runnable[] show = new Runnable[1];
        show[0] = () -> {
            release.run();
            held[0] = false;
            shownAt[0] = System.currentTimeMillis();
            Playlist.Item it = p.items.get(at[0]);
            viewerAt = at[0];
            where.setText((at[0] + 1) + " / " + p.items.size() + "  ·  " + it.title);
            v.setTitle("Playlist viewer · " + p.name + " · " + it.title);
            File f = local(it);
            int k = it.kind();
            videoShown[0] = k == MediaDir.VIDEO || k == MediaDir.SOUND;
            soundShown[0] = k == MediaDir.SOUND;
            for (java.awt.Component c : lookParts) c.setVisible(!soundShown[0]);
            if (loopRef[0] != null) loopRef[0].setText(Playlist.loopLabel(k));
            picture.playhead = -1;
            Playlist.Settings kept = Playlist.settings(dir);
            picture.zoom = MediaDir.clampZoom(videoShown[0] ? kept.zoomVideo : kept.zoom);
            zoomLevel.setText(MediaDir.zoomLabel(picture.zoom));
            timeText.setText(videoShown[0] ? Playlist.time(0, -1) : "");
            if (f == null) {
                message.setText(it.title + " is not there any more");
                viewerNote = "missing";
                cards.show(stage, "message");
                return;
            }
            if (k == MediaDir.PICTURE) {
                try {
                    picture.image = javax.imageio.ImageIO.read(f);
                } catch (Exception ex) {
                    picture.image = null;
                }
                picture.zoomTo(Playlist.settings(dir).zoom);
                viewerNote = picture.image == null ? "unreadable" : "picture";
                if (picture.image == null) {
                    message.setText("Could not show " + it.title);
                    cards.show(stage, "message");
                } else {
                    cards.show(stage, "picture");
                }
                return;
            }
            if (k == MediaDir.SOUND) {
                // Its picture: a drum MIDI's hits (as the MIDI editor shows them), a WAV's or MP3's waveform; the line is its place.
                picture.image = null;
                try {
                    if (f.length() <= SoundPicture.MAX_BYTES) {
                        int pw = 960, ph = 320;
                        int[] px = SoundPicture.pixels(f.getName(), java.nio.file.Files.readAllBytes(f.toPath()), pw, ph);
                        if (px != null) {
                            BufferedImage drawn = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
                            drawn.setRGB(0, 0, pw, ph, px, 0, pw);
                            picture.image = drawn;
                            picture.playhead = 0;
                        }
                    }
                } catch (Exception ex) {
                    picture.image = null;
                }
                picture.zoomTo(1);
                viewerNote = picture.image != null ? "sound picture" : "sound";
            }
            if (k == MediaDir.SOUND && SoundClip.plays(f)) {
                // A WAV or MP3 plays here with Java's own sound, under its picture (no VLC).
                try {
                    SoundClip sc = new SoundClip(f);
                    player[0] = sc;
                    sc.volume(volume.getValue());
                    sc.mute("Unmute".equals(mute.getText()));
                    if (picture.image != null) cards.show(stage, "picture");
                    else {
                        message.setText("<html><center>♪ " + esc(it.title) + "</center></html>");
                        cards.show(stage, "message");
                    }
                    sc.play();
                    return;
                } catch (Throwable ex) {
                    release.run();
                }
            }
            if ((k == MediaDir.VIDEO || k == MediaDir.SOUND) && VlcPlayer.available()) {
                try {
                    VlcPlayer vp = new VlcPlayer(f);
                    player[0] = vp;
                    vp.volume(volume.getValue());
                    vp.mute("Unmute".equals(mute.getText()));
                    vp.screen.zoomTo(picture.zoom);
                    vp.screen.zoomed = () -> {
                        picture.zoom = vp.screen.zoom;
                        zoomLevel.setText(MediaDir.zoomLabel(vp.screen.zoom));
                        keep.run();
                    };
                    videoCard.add(vp.screen, BorderLayout.CENTER);
                    // A sound shows its picture (VLC plays it unseen).
                    cards.show(stage, k == MediaDir.SOUND && picture.image != null ? "picture" : "video");
                    videoCard.revalidate();
                    if (k == MediaDir.VIDEO) viewerNote = "video";
                    if (Playlist.settings(dir).autoplay || k == MediaDir.SOUND) {
                        vp.play();
                        if (!soundShown[0]) vp.rate(Playlist.SPEEDS[Math.max(0, speed.getSelectedIndex())]);
                    }
                    return;
                } catch (Throwable ex) {
                    release.run();
                }
            }
            if (k == MediaDir.SOUND && picture.image != null) {
                // Without VLC: its picture, and how to hear it.
                app.setNow("Install VLC (videolan.org) to play " + it.title + " here");
                cards.show(stage, "picture");
                return;
            }
            message.setText("<html><center>" + esc(it.title) + "<br><br>" + (k == MediaDir.VIDEO || k == MediaDir.SOUND
                ? "Install VLC (videolan.org) to play it here" : "Not a picture, video or sound") + "</center></html>");
            viewerNote = "message";
            cards.show(stage, "message");
        };
        final Runnable goNext = () -> {
            int n = Playlist.next(at[0], p.items.size(), Playlist.settings(dir).repeat);
            if (n < 0) {
                app.setNow("The end of " + p.name);
                return;
            }
            at[0] = n;
            show[0].run();
        };
        prev.addActionListener(e -> {
            int n = Playlist.previous(at[0], p.items.size(), Playlist.settings(dir).repeat);
            if (n < 0) {
                app.setNow("The start of " + p.name);
                return;
            }
            at[0] = n;
            show[0].run();
        });
        next.addActionListener(e -> goNext.run());
        first.addActionListener(e -> {
            if (p.items.isEmpty()) return;
            at[0] = 0;
            show[0].run();
        });
        lastOne.addActionListener(e -> {
            if (p.items.isEmpty()) return;
            at[0] = p.items.size() - 1;
            show[0].run();
        });
        play.addActionListener(e -> {
            if (player[0] == null) return;
            player[0].play();
            if (!soundShown[0]) player[0].rate(Playlist.SPEEDS[Math.max(0, speed.getSelectedIndex())]);
        });
        pause.addActionListener(e -> {
            if (player[0] == null) return;
            if (player[0].playing()) player[0].pause();
            else player[0].play();
        });
        stop.addActionListener(e -> {
            if (player[0] != null) player[0].stop();
        });
        mute.addActionListener(e -> {
            boolean on = !"Unmute".equals(mute.getText());
            mute.setText(on ? "Unmute" : "Mute");
            if (player[0] != null) player[0].mute(on);
            keep.run();
        });
        volume.addChangeListener(e -> {
            if (player[0] != null) player[0].volume(volume.getValue());
            if (!volume.getValueIsAdjusting()) keep.run();
        });
        speed.addActionListener(e -> {
            if (player[0] != null && !soundShown[0]) player[0].rate(Playlist.SPEEDS[Math.max(0, speed.getSelectedIndex())]);
            keep.run();
        });
        Runnable zoomed = () -> {
            zoomLevel.setText(MediaDir.zoomLabel(picture.zoom));
            if (player[0] instanceof VlcPlayer) ((VlcPlayer) player[0]).screen.zoomTo(picture.zoom);
            keep.run();
        };
        zoomOut.addActionListener(e -> {
            picture.zoomTo(MediaDir.zoom(picture.zoom, false));
            zoomed.run();
        });
        zoomIn.addActionListener(e -> {
            picture.zoomTo(MediaDir.zoom(picture.zoom, true));
            zoomed.run();
        });
        fit.addActionListener(e -> {
            picture.zoomTo(1);
            zoomed.run();
        });
        // A picture held: Auto mode waits there (held again: it goes on).
        picture.held = () -> {
            held[0] = !held[0];
            shownAt[0] = System.currentTimeMillis();
            app.setNow(held[0] ? "Held: Auto mode waits on this picture (hold again to go on)" : "Auto mode goes on");
        };
        // Auto mode: a picture for its seconds, a video or a sound to its end, then the next.
        final javax.swing.Timer tick = new javax.swing.Timer(250, e -> {
            if (player[0] != null) timeText.setText(Playlist.time(player[0].timeMs(), player[0].lengthMs()));
            // A sound's place on its picture.
            if (player[0] != null && soundShown[0] && picture.playhead >= 0 && player[0].lengthMs() > 0) {
                picture.playhead = player[0].timeMs() / (double) player[0].lengthMs();
                picture.repaint();
            }
            Playlist.Settings now = Playlist.settings(dir);
            // Loop video: at its end the video starts again (an arrow goes on).
            if (now.loop && player[0] != null && player[0].state() == VlcPlayer.ENDED) {
                player[0].play();
                if (!soundShown[0]) player[0].rate(Playlist.SPEEDS[Math.max(0, speed.getSelectedIndex())]);
                return;
            }
            if (!now.auto || held[0]) return;
            if (player[0] != null) {
                if (player[0].state() == VlcPlayer.ENDED) goNext.run();
                return;
            }
            if (System.currentTimeMillis() - shownAt[0] >= now.seconds * 1000L) goNext.run();
        });
        tick.start();
        v.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                tick.stop();
                release.run();
            }
        });
        // Loop video and Auto mode here too (one at a time), kept as the Viewer tab's.
        final JCheckBox loopBox = new JCheckBox(Playlist.loopLabel(p.items.get(Math.max(0, Math.min(start, p.items.size() - 1))).kind()), s.loop);
        loopRef[0] = loopBox;
        loopBox.setName("viewer-loop");
        final JCheckBox autoBox = new JCheckBox("Auto mode", s.auto);
        autoBox.setName("viewer-auto");
        loopBox.addActionListener(e -> {
            Playlist.Settings now = Playlist.settings(dir);
            now.loop(loopBox.isSelected());
            Playlist.saveSettings(dir, now);
            autoBox.setSelected(now.auto);
            shownAt[0] = System.currentTimeMillis();
        });
        autoBox.addActionListener(e -> {
            Playlist.Settings now = Playlist.settings(dir);
            now.auto(autoBox.isSelected());
            Playlist.saveSettings(dir, now);
            loopBox.setSelected(now.loop);
            shownAt[0] = System.currentTimeMillis();
        });
        // Three rows, each wide enough for its controls: where and Close; playing; sound, speed and zoom.
        JPanel controls = new JPanel(new GridLayout(3, 1, 0, 4));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row1.add(first);
        row1.add(prev);
        row1.add(next);
        row1.add(lastOne);
        row1.add(where);
        row1.add(timeText);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (java.awt.Component c : new java.awt.Component[] {play, pause, stop, loopBox, autoBox}) row2.add(c);
        JButton close = new JButton("Close");
        close.setName("viewer-close");
        close.addActionListener(e -> v.dispose());
        row2.add(close);
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel speedLabel = new JLabel("  Speed");
        JLabel zoomLabel = new JLabel("  Zoom");
        for (java.awt.Component c : new java.awt.Component[] {new JLabel("Volume"), volume, mute, speedLabel, speed, zoomLabel, zoomOut, zoomIn, fit, zoomLevel}) row3.add(c);
        for (java.awt.Component c : new java.awt.Component[] {speedLabel, speed, zoomLabel, zoomOut, zoomIn, fit, zoomLevel}) {
            lookParts.add(c);
            c.setVisible(!soundShown[0]);
        }
        controls.add(row1);
        controls.add(row2);
        controls.add(row3);
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        body.add(stage, BorderLayout.CENTER);
        body.add(controls, BorderLayout.SOUTH);
        v.setContentPane(body);
        show[0].run();
        v.pack();
        v.setLocationRelativeTo(owner);
        lastViewer = v;
        v.setVisible(true);
    }
}
