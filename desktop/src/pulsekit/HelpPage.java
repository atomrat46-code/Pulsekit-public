package pulsekit;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import static pulsekit.Pulsekit.*;

/** The Help page (File > Help-Desktop). */
final class HelpPage {
    /** Help page sections (File > Help-Desktop): caption, then text. */
    static final String[][] HELP_SECTIONS = {
        {"PyJav", "Python, Java, JavaScript, or TypeScript. On Windows, Node.js runs in Termux for Windows: "
            + "pkg install nodejs. npm installs @sogni-ai/sogni-client. A .prompt file runs as bash, cmd, or AI. "
            + "Java runs with the JDK installed on this computer. Python needs Python 3."},
    };

    final Pulsekit app;

    HelpPage(Pulsekit app) {
        this.app = app;
    }

    JPanel buildHelpPage() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(4, 4, 16, 4));
        JLabel title = new JLabel("Help");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(FG);
        title.setAlignmentX(0.0f);
        col.add(title);
        for (String[] section : HELP_SECTIONS) {
            col.add(Box.createVerticalStrut(16));
            JLabel caption = new JLabel(section[0]);
            caption.setFont(new Font("SansSerif", Font.BOLD, 15));
            caption.setForeground(FG);
            caption.setAlignmentX(0.0f);
            col.add(caption);
            col.add(Box.createVerticalStrut(6));
            JLabel text = new JLabel("<html><body style='width:520px'>" + section[1] + "</body></html>");
            text.setForeground(MUTED);
            text.setAlignmentX(0.0f);
            col.add(text);
        }
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        JScrollPane scroll = new JScrollPane(col);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }
}
