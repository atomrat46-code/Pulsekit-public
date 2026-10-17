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
 * category picked last. Favourite categories (★ Favourite on the one shown) are a row on top.
 * Search (on top) finds prompts by name, category, tag (#night), description or text instead.
 */
final class PromptBrowser {
    interface Picked {
        void picked(String title, String text);
    }

    /** The category shown last (0: the first main category). */
    static long lastCategory;

    private PromptBrowser() {}

    /** The app the browser shown last belongs to (its status line, for the favourites' menu). */
    private static Pulsekit app;

    static void show(Pulsekit app, Picked picked) {
        PromptBrowser.app = app;
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
        // Search: names, categories, tags (#night), descriptions and texts; empty shows the categories.
        final javax.swing.JTextField search = new javax.swing.JTextField(PromptSearch.query, 30);
        search.setName("prompt-browser-search");
        search.setToolTipText("Name, category, #tag, description or text");
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                PromptSearch.query = search.getText();
                fill(vault, body, dialog, picked);
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }
        });
        JPanel north = new JPanel(new BorderLayout(6, 0));
        north.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        north.add(new JLabel("Search:"), BorderLayout.WEST);
        north.add(search, BorderLayout.CENTER);
        content.add(north, BorderLayout.NORTH);
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
        if (PromptSearch.query.trim().length() > 0) {
            // The search's prompts: each with its place and tags.
            List<PromptVault.Prompt> hits = PromptSearch.find(vault, PromptSearch.query);
            JLabel found = new JLabel(hits.size() == 1 ? "1 prompt found" : hits.size() + " prompts found");
            found.setName("prompt-browser-found");
            found.setAlignmentX(0.0f);
            body.add(found);
            items(vault, body, dialog, picked, hits, true);
        } else if (mains.isEmpty()) {
            body.add(new JLabel("No categories yet"));
        } else {
            long chosen = vault.category(lastCategory) != null ? lastCategory : mains.get(0).id;
            long mainId = vault.mainOf(chosen);
            // Favourite prompts (versions): a click hands on that version's text; Remove takes it off.
            VersionFavourites.load(PromptDb.dir());
            List<PromptVault.Version> favPrompts = VersionFavourites.list(vault);
            if (!favPrompts.isEmpty()) {
                JLabel favHead = new JLabel("\u2605 Favourite prompts");
                favHead.setAlignmentX(0.0f);
                body.add(favHead);
                for (final PromptVault.Version v : favPrompts) {
                    final String label = VersionFavourites.label(vault, v);
                    final String words = VersionFavourites.words(v);
                    final PromptVault.Prompt p = vault.prompt(v.promptId);
                    JPanel line = new JPanel(new BorderLayout(6, 0));
                    line.setAlignmentX(0.0f);
                    JButton item = new JButton("\u2605 " + label);
                    item.setName("prompt-browser-favprompt:" + label);
                    item.setHorizontalAlignment(JButton.LEFT);
                    item.setEnabled(words.length() > 0);
                    final Runnable use = () -> {
                        dialog.dispose();
                        picked.picked(p == null ? label : p.title, words);
                    };
                    item.addActionListener(e -> use.run());
                    // A right click or Menu: use it, copy its text, move it, remove it from the favourites.
                    final java.util.function.Supplier<javax.swing.JPopupMenu> menu = () -> FavouriteMenu.build(app, vault, v, "Use this prompt", use, () -> fill(vault, body, dialog, picked));
                    FavouriteMenu.attach(item, menu);
                    final JButton more = new JButton("Menu \u25be");
                    more.setName("prompt-browser-favprompt-menu:" + label);
                    more.addActionListener(e -> menu.get().show(more, 0, more.getHeight()));
                    line.add(item, BorderLayout.CENTER);
                    line.add(more, BorderLayout.EAST);
                    line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
                    body.add(line);
                    body.add(Box.createVerticalStrut(4));
                }
            }
            CategoryFavourites.load(PromptDb.dir());
            List<PromptVault.Category> favs = CategoryFavourites.list(vault);
            if (!favs.isEmpty()) {
                JLabel fav = new JLabel("\u2605 Favourites");
                fav.setAlignmentX(0.0f);
                body.add(fav);
                JPanel favRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
                favRow.setAlignmentX(0.0f);
                for (final PromptVault.Category c : favs) {
                    JToggleButton b = new JToggleButton(CategoryFavourites.label(vault, c), c.id == chosen);
                    b.setName("prompt-browser-fav:" + c.name);
                    b.addActionListener(e -> {
                        lastCategory = c.id;
                        fill(vault, body, dialog, picked);
                    });
                    favRow.add(b);
                }
                body.add(favRow);
            }
            body.add(row(vault, mains, mainId, "prompt-browser-tab:", body, dialog, picked));
            // Subcategories nest: a row for each level down to the category chosen (and its own under it).
            List<PromptVault.Category> path = vault.pathTo(chosen);
            for (int k = 0; k < path.size(); k++) {
                PromptVault.Category at = path.get(k);
                List<PromptVault.Category> subs = vault.children(at.id);
                if (subs.isEmpty()) continue;
                JLabel under = new JLabel("Subcategories of " + at.name);
                under.setAlignmentX(0.0f);
                body.add(under);
                body.add(row(vault, subs, k + 1 < path.size() ? path.get(k + 1).id : -1, "prompt-browser-sub:", body, dialog, picked));
            }
            PromptVault.Category shown = vault.category(chosen);
            JLabel heading = new JLabel("PROMPTS IN " + (shown == null ? "" : shown.name.toUpperCase(java.util.Locale.ROOT)));
            JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            head.setAlignmentX(0.0f);
            head.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
            head.add(heading);
            if (shown != null) {
                final long id = shown.id;
                boolean on = CategoryFavourites.is(id);
                JButton star = new JButton(on ? "\u2605 Favourite" : "\u2606 Add to favourites");
                star.setName("prompt-browser-star");
                star.setToolTipText(on ? "Take it off the favourites" : "Show it in the favourites row");
                star.addActionListener(e -> {
                    CategoryFavourites.toggle(id);
                    fill(vault, body, dialog, picked);
                });
                head.add(Box.createHorizontalStrut(12));
                head.add(star);
            }
            head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
            body.add(head);
            List<PromptVault.Prompt> prompts = vault.prompts(chosen);
            if (prompts.isEmpty()) {
                JLabel none = new JLabel("No prompts here");
                none.setAlignmentX(0.0f);
                body.add(none);
            }
            items(vault, body, dialog, picked, prompts, false);
        }
        body.revalidate();
        body.repaint();
    }

    /** The prompts as buttons: name, (in search results) its place, its tags, its first words. */
    private static void items(final PromptVault vault, final JPanel body, final JDialog dialog, final Picked picked, List<PromptVault.Prompt> prompts, boolean place) {
        for (final PromptVault.Prompt p : prompts) {
            final String words = PromptSearch.words(vault, p);
            String first = words.replace('\n', ' ');
            if (first.length() > 70) first = first.substring(0, 70) + "…";
            String about = place ? PromptSearch.where(vault, p) : "";
            String tags = PromptSearch.tagLine(vault, p);
            if (tags.length() > 0) about = about.length() == 0 ? tags : about + " \u00b7 " + tags;
            JButton item = new JButton("<html><b>" + esc(p.title == null ? "Prompt" : p.title) + "</b>" + (about.length() > 0 ? "<br><span style='color:#D9A441'>" + esc(about) + "</span>" : "")
                + (first.length() > 0 ? "<br>" + esc(first) : "") + "</html>");
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
