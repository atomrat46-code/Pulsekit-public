package pulsekit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/**
 * File > Sound devices: the outputs, inputs, MIDI devices, volume ports and drivers found (SoundOut),
 * a click shows a device's driver and formats. Use for Pulsekit plays Pulsekit's sound (or the drum
 * tracks, for a MIDI output) through it; Use the default goes back to the system's output and
 * Pulsekit's own synth. Test plays a tone, sends a snare, or listens a second. System sound settings
 * opens the system's own; Browse driver\u2026 lists a driver file; Copy report copies everything. What is
 * found and done goes into the logs (Logging on) for sending.
 */
final class SoundDevicesScreen {
    private SoundDevicesScreen() {}

    /** The answers the tests give (null: the system's). */
    static java.util.function.Function<String, File> browseTo;

    static void show(final Pulsekit app) {
        final JDialog dialog = new JDialog(app, "Sound devices", true);
        dialog.setName("sound-devices");
        final JLabel now = new JLabel();
        now.setName("sound-now");
        final DefaultListModel<Object> model = new DefaultListModel<Object>();
        final JList<Object> list = new JList<Object>(model);
        list.setName("sound-list");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                boolean head = v instanceof String;
                java.awt.Component c = super.getListCellRendererComponent(l, head ? v : "    " + ((SoundDevices.Device) v).label(), i, sel && !head, focus && !head);
                c.setFont(c.getFont().deriveFont(head ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
                return c;
            }
        });
        final JTextArea info = new JTextArea();
        info.setName("sound-info");
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        final java.util.List<SoundDevices.Device> found = new java.util.ArrayList<SoundDevices.Device>();
        final JButton use = new JButton("Use for Pulsekit");
        use.setName("sound-use");
        final JButton test = new JButton("Test");
        test.setName("sound-test");
        final Runnable fill = () -> {
            AppSettings.load(PromptDb.dir());
            found.clear();
            found.addAll(SoundOut.list());
            Object was = list.getSelectedValue();
            model.clear();
            for (String kind : SoundDevices.KINDS) {
                List<SoundDevices.Device> some = SoundDevices.of(found, kind);
                model.addElement(kind + " (" + some.size() + ")");
                for (SoundDevices.Device d : some) model.addElement(d);
            }
            now.setText("<html>Pulsekit's sound output: <b>" + esc(SoundDevices.outNow()) + "</b> &nbsp; Drum MIDI output: <b>" + esc(SoundDevices.midiNow()) + "</b>"
                + (SoundDevices.missing(found).isEmpty() ? "" : "<br>" + esc(SoundDevices.missing(found)).replace("\n", "<br>")) + "</html>");
            for (int i = 0; i < model.size(); i++) {
                Object o = model.get(i);
                if (was instanceof SoundDevices.Device && o instanceof SoundDevices.Device && ((SoundDevices.Device) o).key().equals(((SoundDevices.Device) was).key())
                    && ((SoundDevices.Device) o).kind.equals(((SoundDevices.Device) was).kind)) list.setSelectedIndex(i);
            }
            if (list.getSelectedValue() == null) info.setText(found.isEmpty() ? "No sound devices found (Java Sound lists none on this computer)." : "Click a device for its driver and formats.");
            list.repaint();
        };
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Object v = list.getSelectedValue();
            if (!(v instanceof SoundDevices.Device)) {
                use.setEnabled(false);
                test.setEnabled(false);
                return;
            }
            SoundDevices.Device d = (SoundDevices.Device) v;
            info.setText(d.name + "\n" + d.kind + (d.driver.isEmpty() ? "" : ", driver: " + d.driver) + (SoundDevices.used(d) ? "\nUsed by Pulsekit" : "") + "\n\n" + d.details);
            info.setCaretPosition(0);
            use.setEnabled(SoundDevices.usable(d) && !SoundDevices.used(d));
            test.setEnabled(SoundDevices.OUT.equals(d.kind) || SoundDevices.IN.equals(d.kind) || SoundDevices.MIDI_OUT.equals(d.kind));
        });
        use.addActionListener(e -> {
            Object v = list.getSelectedValue();
            if (!(v instanceof SoundDevices.Device)) return;
            SoundDevices.Device d = (SoundDevices.Device) v;
            boolean midi = SoundDevices.MIDI_OUT.equals(d.kind);
            // Pulsekit's own synth is a MIDI output too: picked, it is the same as the default.
            boolean own = midi && SoundDevices.SYNTH.equals(d.driver);
            if (midi) AppSettings.setMidiOut(own ? "" : d.key());
            else AppSettings.setSoundOut(d.key());
            app.playback.reopenMidi();
            String said = midi ? "The drum tracks play through " + (own ? SoundDevices.OWN_SYNTH : d.name) : "Pulsekit's sound plays through " + d.name;
            SoundDevices.log(said, SoundDevices.report("", found));
            app.setNow(said);
            fill.run();
        });
        JButton reset = new JButton("Use the default");
        reset.setName("sound-default");
        reset.setToolTipText("The system's default output, and Pulsekit's own synth for the drums");
        reset.addActionListener(e -> {
            AppSettings.setSoundOut("");
            AppSettings.setMidiOut("");
            app.playback.reopenMidi();
            SoundDevices.log("Back to the defaults", SoundDevices.DEFAULT_OUT + ", " + SoundDevices.OWN_SYNTH);
            app.setNow("Pulsekit plays through the system default output and its own synth");
            fill.run();
        });
        test.addActionListener(e -> {
            Object v = list.getSelectedValue();
            if (!(v instanceof SoundDevices.Device)) return;
            final SoundDevices.Device d = (SoundDevices.Device) v;
            // An input: a window with a level meter, Record, Stop, Play and Save.
            if (SoundDevices.IN.equals(d.kind)) {
                InputTestScreen.show(app, dialog, d);
                return;
            }
            test.setEnabled(false);
            app.setNow("Testing " + d.name + "\u2026");
            new Thread(() -> {
                String said = SoundOut.test(d);
                SoundDevices.log("Test: " + d.name, said + "\n\n" + d.details);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    test.setEnabled(true);
                    info.setText(info.getText() + "\n\n" + said);
                    app.setNow(said);
                });
            }, "sound-test").start();
        });
        JButton system = new JButton("System sound settings\u2026");
        system.setName("sound-system");
        system.addActionListener(e -> {
            String said = SoundOut.openSystemSettings();
            SoundDevices.log("System sound settings", said);
            app.setNow(said);
        });
        JButton browse = new JButton("Browse driver\u2026");
        browse.setName("sound-browse");
        browse.setToolTipText("A driver file (.dll, .so, .dylib) listed under Drivers, with its size and date, for the report");
        browse.addActionListener(e -> {
            File f = browseTo != null ? browseTo.apply("driver") : pick(dialog);
            if (f == null) return;
            VstLists.remember(SoundOut.DRIVERS_KEY, f.getAbsolutePath());
            SoundDevices.log("Driver file picked", f.getAbsolutePath() + " (" + f.length() + " bytes)");
            app.setNow(f.getName() + " is listed under Drivers");
            fill.run();
        });
        JButton copy = new JButton("Copy report");
        copy.setName("sound-copy");
        copy.addActionListener(e -> {
            String report = SoundDevices.report(header(), found);
            try {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(report), null);
                app.setNow("Copied the sound devices report (" + found.size() + " devices)");
            } catch (Exception ex) {
                app.setNow("Cannot copy: " + ex.getMessage());
            }
        });
        JButton refresh = new JButton("Refresh");
        refresh.setName("sound-refresh");
        refresh.addActionListener(e -> fill.run());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        use.setEnabled(false);
        test.setEnabled(false);
        fill.run();
        SoundDevices.log("Sound devices found (" + found.size() + ")", SoundDevices.report(header(), found));
        JScrollPane left = new JScrollPane(list);
        left.setPreferredSize(new Dimension(460, 440));
        JScrollPane right = new JScrollPane(info);
        right.setPreferredSize(new Dimension(460, 440));
        JPanel north = new JPanel(new BorderLayout());
        north.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        north.add(now, BorderLayout.CENTER);
        // Pulsekit's own levels: its players' volume, the gain on what it records.
        JPanel levels = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        levels.add(level(app, InputLevel.PLAYBACK, "sound-play-volume", 100, AppSettings.playVolume(), p -> {
            AppSettings.setPlayVolume(p);
            SoundOut.volumeChanged();
        }));
        levels.add(level(app, InputLevel.RECORDING, "sound-record-level", 400, AppSettings.recordLevel(), AppSettings::setRecordLevel));
        levels.setToolTipText(InputLevel.LEVELS_NOTE);
        north.add(levels, BorderLayout.SOUTH);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        for (JButton b : new JButton[] {use, reset, test, system, browse, copy, refresh, close}) south.add(b);
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        content.add(north, BorderLayout.NORTH);
        content.add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right), BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
    }

    /** A level: its name and percent, and a slider (0 to `max` percent) that keeps what is set. */
    static JPanel level(final Pulsekit app, final String name, String tag, int max, int now, final java.util.function.IntConsumer set) {
        final javax.swing.JLabel label = new javax.swing.JLabel(name + ": " + now + "%");
        label.setName(tag + "-label");
        final javax.swing.JSlider slider = new javax.swing.JSlider(0, max, now);
        slider.setName(tag);
        slider.setPreferredSize(new Dimension(180, slider.getPreferredSize().height));
        slider.addChangeListener(e -> {
            label.setText(name + ": " + slider.getValue() + "%");
            set.accept(slider.getValue());
            if (!slider.getValueIsAdjusting()) app.setNow(name + " " + slider.getValue() + "%");
        });
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.add(label);
        p.add(slider);
        return p;
    }

    /** The report's first lines: Pulsekit's build, the system and Java. */
    static String header() {
        return "Pulsekit " + Looks.version() + " (desktop) on " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")
            + ", Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")";
    }

    static File pick(java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("A sound driver file");
        fc.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Driver files (dll, so, dylib, sys, inf)", "dll", "so", "dylib", "sys", "inf"));
        return fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
