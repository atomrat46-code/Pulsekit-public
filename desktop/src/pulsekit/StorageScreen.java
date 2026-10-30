package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * General settings > Storage (Storage), as on Android: what Pulsekit's folders take, Clear
 * temporary files, and how old a temporary file gets before Pulsekit deletes it when it starts.
 */
final class StorageScreen {
    private StorageScreen() {}

    /** The tests: what the last clearing said, the answer to Delete (null: ask), the sizes counted last. */
    static volatile String lastSaid;
    static Boolean answer;
    static volatile String lastSizes;

    static List<Storage.Place> places() {
        File root = PromptDb.dir();
        File logs = Logs.dir();
        List<Storage.Place> out = new ArrayList<Storage.Place>();
        out.add(new Storage.Place("Prompt library (all DBs)", root, false).dbOnly());
        out.add(new Storage.Place("Settings, songs, patterns and playlists", root, false).noDb().skip(logs.getName(), "pyjav-in", "thumbs"));
        out.add(new Storage.Place("Logs", logs, false));
        out.add(new Storage.Place("Video previews", ThumbCache.dir(), false));
        out.add(new Storage.Place("Program inputs and outputs", new File(root, "pyjav-in"), true));
        out.add(new Storage.Place("Other temporary files", new File(System.getProperty("java.io.tmpdir", ".")), true).prefix("pulsekit"));
        return out;
    }

    /** The Storage section of General settings; its controls (the master password greys them). */
    static List<JComponent> addTo(final Pulsekit app, JPanel more) {
        more.add(GeneralSettings.headline(Storage.TITLE));
        final JTextArea sizes = GeneralSettings.note("Counting…");
        sizes.setName("storage-sizes");
        more.add(sizes);
        count(sizes);
        final JButton clear = new JButton(Storage.CLEAR);
        clear.setName("storage-clear");
        clear.addActionListener(e -> askClear(app, sizes));
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 4));
        row.add(clear);
        more.add(row);
        String[] labels = new String[Storage.DAYS.length];
        int at = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Storage.daysLabel(Storage.DAYS[i]);
            if (Storage.DAYS[i] == AppSettings.tempDays()) at = i;
        }
        final JComboBox<String> days = new JComboBox<String>(labels);
        days.setName("storage-days");
        days.setSelectedIndex(at);
        days.addActionListener(e -> {
            int d = Storage.DAYS[days.getSelectedIndex()];
            if (d == AppSettings.tempDays()) return;
            AppSettings.setTempDays(d);
            app.setNow(d == 0 ? "Temporary files are kept until Clear temporary files" : "Temporary files older than " + Storage.daysLabel(d) + " are deleted when Pulsekit starts");
        });
        JPanel daysRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4));
        daysRow.add(new javax.swing.JLabel(Storage.AUTO));
        daysRow.add(days);
        more.add(daysRow);
        more.add(GeneralSettings.note(Storage.NOTE.replace("(their results are ", "(their results are\n")));
        List<JComponent> controls = new ArrayList<JComponent>();
        controls.add(clear);
        controls.add(days);
        return controls;
    }

    /** The sizes counted on a thread of their own (a big input folder takes a moment). */
    static void count(final JTextArea into) {
        new Thread(() -> {
            final String text = String.join("\n", Storage.lines(places()));
            lastSizes = text;
            SwingUtilities.invokeLater(() -> into.setText(text));
        }, "pulsekit-storage").start();
    }

    static void askClear(Pulsekit app, JTextArea sizes) {
        boolean yes = answer != null ? answer : JOptionPane.showConfirmDialog(app, Storage.ASK, Storage.CLEAR, JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
        if (!yes) return;
        lastSaid = Storage.said(Storage.clear(places(), Long.MAX_VALUE), null);
        app.setNow(lastSaid);
        count(sizes);
    }

    /** When Pulsekit starts: the temporary files older than the days set deleted (on a thread of its own). */
    static void startup(final Pulsekit app) {
        final int days = AppSettings.tempDays();
        if (days <= 0) return;
        new Thread(() -> {
            final String text = Storage.clearOld(places(), days);
            if (text == null) return;
            Logs.write(Logs.STARTS, text, text);
            SwingUtilities.invokeLater(() -> app.setNow(text));
        }, "pulsekit-storage-startup").start();
    }
}
