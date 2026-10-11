package pulsekit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;

/**
 * Browse prompts: the prompt library's main categories as tabs, the chosen one's subcategories
 * under it, and the prompts in the category picked; clicking a prompt hands its text (its final
 * version) to the field that asked (Params' Prompt, the Prompts page's Prompt). Opens on the
 * category picked last.
 */
final class PromptBrowser {
    interface Picked {
        void picked(String title, String text);
    }

    /** The category shown last (0: the first main category). */
    static long lastCategory;

    private PromptBrowser() {}

    static void show(Pulsekit app, Picked picked) {
        final PromptVault vault;
        try {
            vault = PromptDb.vault();
        } catch (Exception ex) {
            app.setNow("Could not open the prompt library");
            return;
        }
        final JDialog dialog = new JDialog(app, "Browse prompts", true);
        dialog.setName("prompt-browser");
        final JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setPreferredSize(new Dimension(620, 460));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(scroll, BorderLayout.CENTER);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        south.add(cancel);
        content.add(south, BorderLayout.SOUTH);
        fill(vault, body, dialog, picked);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
    }

    private static void fill(final PromptVault vault, final JPanel body, final JDialog dialog, final Picked picked) {
        body.removeAll();
        List<PromptVault.Category> mains = vault.mains();
        if (mains.isEmpty()) {
            body.add(new JLabel("No categories yet"));
        } else {
            long chosen = vault.category(lastCategory) != null ? lastCategory : mains.get(0).id;
            long mainId = vault.mainOf(chosen);
            body.add(row(vault, mains, mainId, "prompt-browser-tab:", body, dialog, picked));
            PromptVault.Category main = vault.category(mainId);
            List<PromptVault.Category> subs = vault.children(mainId);
            if (!subs.isEmpty()) {
                JLabel under = new JLabel("Subcategories of " + main.name);
                under.setAlignmentX(0.0f);
                body.add(under);
                body.add(row(vault, subs, chosen, "prompt-browser-sub:", body, dialog, picked));
            }
            PromptVault.Category shown = vault.category(chosen);
            JLabel heading = new JLabel("PROMPTS IN " + (shown == null ? "" : shown.name.toUpperCase(java.util.Locale.ROOT)));
            heading.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
            heading.setAlignmentX(0.0f);
            body.add(heading);
            List<PromptVault.Prompt> prompts = vault.prompts(chosen);
            if (prompts.isEmpty()) {
                JLabel none = new JLabel("No prompts here");
                none.setAlignmentX(0.0f);
                body.add(none);
            }
            for (final PromptVault.Prompt p : prompts) {
                PromptVault.Version v = vault.finalVersion(p.id);
                final String words = v == null ? "" : PromptRun.withoutDescription(v.description, v.body == null ? "" : v.body).trim();
                String first = words.replace('\n', ' ');
                if (first.length() > 70) first = first.substring(0, 70) + "…";
                JButton item = new JButton("<html><b>" + esc(p.title == null ? "Prompt" : p.title) + "</b>" + (first.length() > 0 ? "<br>" + esc(first) : "") + "</html>");
                item.setName("prompt-browser-item:" + p.title);
                item.setHorizontalAlignment(JButton.LEFT);
                item.setAlignmentX(0.0f);
                item.setMaximumSize(new Dimension(Integer.MAX_VALUE, item.getPreferredSize().height));
                item.setEnabled(words.length() > 0);
                item.addActionListener(e -> {
                    dialog.dispose();
                    picked.picked(p.title, words);
                });
                body.add(item);
                body.add(Box.createVerticalStrut(4));
            }
        }
        body.revalidate();
        body.repaint();
    }

    private static JPanel row(final PromptVault vault, List<PromptVault.Category> cats, long on, String tag, final JPanel body, final JDialog dialog, final Picked picked) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        row.setAlignmentX(0.0f);
        for (final PromptVault.Category c : cats) {
            JToggleButton b = new JToggleButton(c.name, c.id == on);
            b.setName(tag + c.name);
            b.addActionListener(e -> {
                lastCategory = c.id;
                fill(vault, body, dialog, picked);
            });
            row.add(b);
        }
        return row;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
