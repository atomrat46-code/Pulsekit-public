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
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
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
        if (runs.isEmpty()) {
            JLabel none = new JLabel(SogniDownloads.PROGRESS.equals(tab) ? "No run in progress" : SogniDownloads.PENDING.equals(tab) ? "Nothing pending" : "No finished downloads");
            none.setName("downloads-empty");
            body.add(none);
        }
        String program = null;
        for (final SogniDownloads.Run r : runs) {
            if (!r.programName().equals(program)) {
                program = r.programName();
                JLabel head = new JLabel("<html><b>" + program + "</b></html>");
                head.setName("downloads-program:" + program);
                head.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
                head.setAlignmentX(0.0f);
                body.add(head);
            }
            JLabel line = new JLabel(SogniDownloads.runLabel(r));
            line.setName("downloads-run:" + r.id);
            line.setAlignmentX(0.0f);
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
            for (final SogniDownloads.Item item : r.items) {
                final JButton b = new JButton("<html>" + esc(item.name) + "<br><small>" + esc(item.shown) + "</small></html>");
                b.setName("downloads-item:" + item.name);
                b.setHorizontalAlignment(JButton.LEFT);
                b.setAlignmentX(0.0f);
                b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
                b.addActionListener(e -> preview(app, item));
                if (SogniDownloads.FINISHED.equals(tab)) {
                    b.setToolTipText("A right click: Preview, Clear, Rename, Delete, Move to, Copy to");
                    b.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mousePressed(java.awt.event.MouseEvent e) {
                            if (SwingUtilities.isRightMouseButton(e)) menu(app, body, r, item).show(b, e.getX(), e.getY());
                        }
                    });
                }
                body.add(b);
                body.add(Box.createVerticalStrut(3));
            }
        }
        body.revalidate();
        body.repaint();
    }

    static final String[] MENU = {"Preview", "Clear", "Rename", "Delete", "Move to…", "Copy to…"};

    /** A finished file's menu. */
    static JPopupMenu menu(final Pulsekit app, final JPanel body, final SogniDownloads.Run r, final SogniDownloads.Item item) {
        JPopupMenu m = new JPopupMenu();
        for (int i = 0; i < MENU.length; i++) {
            final int which = i;
            JMenuItem it = new JMenuItem(MENU[i]);
            it.setName("downloads-menu:" + MENU[i]);
            it.addActionListener(e -> {
                if (which == 0) preview(app, item);
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
