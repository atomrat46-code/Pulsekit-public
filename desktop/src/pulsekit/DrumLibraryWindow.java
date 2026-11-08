package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * File > Drum sound library (DrumLibrary): its parts (with how many sounds and how many are ready),
 * a part's sounds with their measures (sorted as asked), each with ▶, Part… (another part), Again
 * (measured again) and ✕ (taken out of the library; the file stays); Analyze waiting. A pad's menu
 * adds sounds: a file, a folder (with its subfolders or not) or the prompt library's sound files.
 */
final class DrumLibraryWindow {
    private DrumLibraryWindow() {}

    /** The window shown last, what was said last, and the analysis running (for the tests). */
    static JDialog last;
    static volatile String lastSaid = "";
    static volatile Thread analyzing;
    static Runnable refill;

    static File work() {
        return new File(PromptDb.dir(), "drumlibrary");
    }

    static void open() {
        DrumLibrary.open(PromptDb.dir());
    }

    /** The pad's menu: Add sounds to the library… and its sources. */
    static JMenu padMenu(final Pulsekit app, final int pad) {
        JMenu add = new JMenu(DrumLibrary.ADD);
        add.setName("pad-library:" + pad);
        String[] kinds = {DrumLibrary.ADD_FILE, DrumLibrary.ADD_FOLDER, DrumLibrary.ADD_TREE, DrumLibrary.ADD_DB};
        for (final String k : kinds) {
            JMenuItem it = new JMenuItem(k);
            it.setName("pad-library-" + (k == DrumLibrary.ADD_FILE ? "file" : k == DrumLibrary.ADD_FOLDER ? "folder" : k == DrumLibrary.ADD_TREE ? "tree" : "db") + ":" + pad);
            it.addActionListener(e -> addFrom(app, pad, k));
            add.add(it);
        }
        return add;
    }

    /** Sounds added to the pad's part from a file, a folder (or its tree) or the DB; then measured. */
    static void addFrom(final Pulsekit app, int pad, String kind) {
        open();
        final String part = DrumLibrary.partOfPad(pad);
        if (kind.equals(DrumLibrary.ADD_DB)) {
            app.promptDb.browse(PromptDb.SOUNDS, (name, file) -> {
                DrumLibrary.Entry e = DrumLibrary.add(DrumLibrary.DB + name, name, part, "db");
                DrumLibrary.save();
                said(app, e == null ? "Already in the library: " + name : "Added " + name + " to " + DrumLibrary.label(e.part));
                analyze(app);
            });
            return;
        }
        boolean folder = !kind.equals(DrumLibrary.ADD_FILE);
        JFileChooser c = new JFileChooser(MediaBrowser.downloads());
        c.setDialogTitle(DrumLibrary.ADD + " " + DrumLibrary.label(part));
        if (folder) c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        else c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("WAV, MP3", "wav", "wave", "mp3"));
        if (c.showOpenDialog(app) != JFileChooser.APPROVE_OPTION || c.getSelectedFile() == null) return;
        File picked = c.getSelectedFile();
        List<File> files = folder ? DrumLibrary.soundFiles(picked, kind.equals(DrumLibrary.ADD_TREE)) : java.util.Collections.singletonList(picked);
        if (files.isEmpty()) {
            said(app, "No WAV or MP3 in " + picked.getName());
            return;
        }
        said(app, DrumLibrary.addFiles(files, part, folder ? "folder" : "file"));
        analyze(app);
    }

    static void said(Pulsekit app, String s) {
        lastSaid = s;
        app.setNow(s);
        if (refill != null && last != null && last.isDisplayable()) refill.run();
    }

    /** The app's way to the sounds: a file as it is, a DB file copied into `work`. */
    static DrumLibrary.Access access() {
        return new DrumLibrary.Access() {
            public File local(DrumLibrary.Entry e, File work) throws Exception {
                if (e.link.startsWith(DrumLibrary.DB)) {
                    String name = e.link.substring(DrumLibrary.DB.length());
                    for (PromptVault.StoredFile f : PromptDb.allFiles(PromptDb.SOUNDS)) {
                        if (!f.name.equals(name)) continue;
                        File copy = new File(work, System.nanoTime() + "-" + name.replaceAll("[\\\\/:*?\"<>|]", "_"));
                        Files.write(copy.toPath(), PromptDb.vault().fileBytes(f.versionId, f.which));
                        return copy;
                    }
                    throw new java.io.FileNotFoundException("not in the DB any more");
                }
                File f = new File(e.link);
                if (!f.isFile()) throw new java.io.FileNotFoundException("the file is gone");
                return f;
            }

            public byte[] jar() {
                try {
                    return ProgramFiles.read("Java", "DrumMidi_CRT.jar");
                } catch (Exception ex) {
                    return null;
                }
            }
        };
    }

    /** The waiting sounds measured, off the Swing thread (one analysis at a time). */
    static synchronized void analyze(final Pulsekit app) {
        if (analyzing != null && analyzing.isAlive()) return;
        final List<DrumLibrary.Entry> todo = DrumLibrary.waiting();
        if (todo.isEmpty()) return;
        analyzing = new Thread(() -> {
            String s = DrumLibrary.analyze(todo, access(), work(), done -> SwingUtilities.invokeLater(() -> said(app, "Analyzing drum sounds: " + done + " of " + todo.size())));
            SwingUtilities.invokeLater(() -> said(app, s));
            if (!DrumLibrary.waiting().isEmpty()) SwingUtilities.invokeLater(() -> analyze(app));
        }, "pulsekit-drum-library");
        analyzing.start();
    }

    static void show(final Pulsekit app) {
        open();
        if (last != null && last.isDisplayable()) last.dispose();
        final JDialog d = new JDialog(app, DrumLibrary.TITLE, false);
        d.setName("drumlibrary");
        last = d;
        final JComboBox<String> part = new JComboBox<String>();
        part.setName("library-part");
        final JComboBox<String> sort = new JComboBox<String>(DrumLibrary.SORTS);
        sort.setName("library-sort");
        final JLabel status = new JLabel(" ");
        status.setName("library-status");
        final JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setName("library-rows");
        final boolean[] filling = {false};
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            filling[0] = true;
            int at = Math.max(0, part.getSelectedIndex());
            part.removeAllItems();
            part.addItem(DrumLibrary.partLine(null));
            for (String p : DrumLibrary.PARTS) part.addItem(DrumLibrary.partLine(p));
            part.setSelectedIndex(at);
            filling[0] = false;
            String chosen = at == 0 ? null : DrumLibrary.PARTS[at - 1];
            rows.removeAll();
            List<DrumLibrary.Entry> list = DrumLibrary.of(chosen, Math.max(0, sort.getSelectedIndex()));
            for (DrumLibrary.Entry e : list) rows.add(row(app, e, status, fill));
            if (list.isEmpty()) rows.add(new JLabel("No sounds here yet: a pad's menu (right click) has " + DrumLibrary.ADD));
            int[] c = DrumLibrary.counts(null);
            status.setText(lastSaid.isEmpty() ? c[0] + " sounds, " + c[1] + " ready, " + c[2] + " waiting" : lastSaid);
            rows.revalidate();
            rows.repaint();
        };
        refill = fill[0];
        part.addActionListener(e -> {
            if (!filling[0]) fill[0].run();
        });
        sort.addActionListener(e -> fill[0].run());
        JButton analyze = new JButton(DrumLibrary.ANALYZE);
        analyze.setName("library-analyze");
        analyze.addActionListener(e -> analyze(app));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        top.add(new JLabel("Part"));
        top.add(part);
        top.add(new JLabel("Sort by"));
        top.add(sort);
        top.add(analyze);
        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(status, BorderLayout.SOUTH);
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        body.add(north, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(rows);
        scroll.setPreferredSize(new java.awt.Dimension(900, 420));
        body.add(scroll, BorderLayout.CENTER);
        d.setContentPane(body);
        fill[0].run();
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                refill = null;
                AudioMixerWindow.stop();
            }
        });
        d.pack();
        d.setLocationRelativeTo(app);
        d.setVisible(true);
    }

    static JPanel row(final Pulsekit app, final DrumLibrary.Entry e, final JLabel status, final Runnable[] fill) {
        JPanel r = new JPanel(new BorderLayout(6, 0));
        r.setName("library-row:" + e.name);
        r.setAlignmentX(0.0f);
        JLabel line = new JLabel((e.ready() ? "✔ " : "• ") + DrumLibrary.label(e.part) + " · " + DrumLibrary.line(e));
        line.setToolTipText(e.link);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton play = new JButton("▶");
        play.setName("library-play:" + e.name);
        play.addActionListener(ev -> new Thread(() -> {
            String s;
            try {
                File work = work();
                work.mkdirs();
                File f = access().local(e, work);
                byte[] bytes = Files.readAllBytes(f.toPath());
                if (f.getParentFile() != null && f.getParentFile().getAbsoluteFile().equals(work.getAbsoluteFile())) f.delete();
                AudioIo.StereoPcm p = AudioMix.resample(AudioMix.decode(e.name, bytes, null), AudioMix.RATE);
                SwingUtilities.invokeLater(() -> AudioMixerWindow.play(p.left, p.right, status, e.name));
                return;
            } catch (Exception ex) {
                s = "Could not play " + e.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            final String shown = s;
            SwingUtilities.invokeLater(() -> status.setText(shown));
        }, "pulsekit-library-play").start());
        JButton move = new JButton("Part…");
        move.setName("library-move:" + e.name);
        move.addActionListener(ev -> {
            Object picked = javax.swing.JOptionPane.showInputDialog(last, "The part for " + e.name, "Part", javax.swing.JOptionPane.PLAIN_MESSAGE, null, DrumLibrary.PART_LABELS,
                DrumLibrary.label(e.part));
            if (picked == null) return;
            for (int i = 0; i < DrumLibrary.PARTS.length; i++) if (DrumLibrary.PART_LABELS[i].equals(picked)) DrumLibrary.move(e, DrumLibrary.PARTS[i]);
            fill[0].run();
        });
        JButton again = new JButton("Again");
        again.setName("library-again:" + e.name);
        again.addActionListener(ev -> {
            e.state = DrumLibrary.WAITING;
            DrumLibrary.save();
            fill[0].run();
            analyze(app);
        });
        JButton out = new JButton("✕");
        out.setName("library-remove:" + e.name);
        out.setToolTipText("Take it out of the library (the file stays)");
        out.addActionListener(ev -> {
            DrumLibrary.remove(e);
            fill[0].run();
        });
        buttons.add(play);
        buttons.add(move);
        buttons.add(again);
        buttons.add(out);
        r.add(line, BorderLayout.CENTER);
        r.add(buttons, BorderLayout.EAST);
        r.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
        return r;
    }
}
