package pulsekit;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.text.JTextComponent;

/** Right click on a results text: save it as a .txt file, such as CompareHits_test_results.txt. */
final class SaveText {
    /** The file name to offer; null when there is nothing to save. */
    interface Name {
        String get();
    }

    private SaveText() {}

    static void attach(final Pulsekit app, final JTextComponent view, final Name name) {
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) show(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) show(e);
            }

            void show(MouseEvent e) {
                String text = view.getText() == null ? "" : view.getText();
                if (text.trim().length() == 0) return;
                final String file = name.get();
                if (file == null) return;
                JPopupMenu menu = new JPopupMenu();
                JMenuItem save = new JMenuItem("Save as " + file);
                save.addActionListener(a -> save(app, file, view.getText()));
                menu.add(save);
                menu.show(view, e.getX(), e.getY());
            }
        });
    }

    static void save(Pulsekit app, String file, String text) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), file));
        if (chooser.showSaveDialog(app) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.write(chooser.getSelectedFile().toPath(), (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            app.setNow("Saved results");
        } catch (Exception ex) {
            app.setNow("Could not save the results");
        }
    }
}
