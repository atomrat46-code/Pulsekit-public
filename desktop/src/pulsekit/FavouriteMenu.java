package pulsekit;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * A favourite prompt's menu (a right click, or its Menu button), as on the phone: open it
 * (Browse prompts: use its text), copy its prompt text, move it up or down the list, or remove it
 * from the favourites.
 */
final class FavouriteMenu {
    /** The menu shown last, for the tests. */
    static JPopupMenu last;

    private FavouriteMenu() {}

    static JPopupMenu build(final Pulsekit app, final PromptVault vault, final PromptVault.Version v, String openLabel, final Runnable open, final Runnable changed) {
        final String label = VersionFavourites.label(vault, v);
        final String words = VersionFavourites.words(v);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem o = new JMenuItem(openLabel);
        o.addActionListener(e -> open.run());
        JMenuItem copy = new JMenuItem("Copy prompt text");
        copy.addActionListener(e -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(words), null);
            app.setNow(words.length() == 0 ? label + " has no prompt text" : "Copied the prompt of " + label);
        });
        JMenuItem up = new JMenuItem("Move up");
        up.addActionListener(e -> {
            if (!VersionFavourites.move(v.id, -1)) app.setNow("Already the first");
            changed.run();
        });
        JMenuItem down = new JMenuItem("Move down");
        down.addActionListener(e -> {
            if (!VersionFavourites.move(v.id, 1)) app.setNow("Already the last");
            changed.run();
        });
        JMenuItem remove = new JMenuItem("Remove from favourites");
        remove.addActionListener(e -> {
            VersionFavourites.toggle(v.id);
            app.setNow("Removed " + label + " from favourites");
            changed.run();
        });
        menu.add(o);
        menu.add(copy);
        menu.addSeparator();
        menu.add(up);
        menu.add(down);
        menu.addSeparator();
        menu.add(remove);
        last = menu;
        return menu;
    }

    /** The menu on a right click of `c`. */
    static void attach(final JComponent c, final java.util.function.Supplier<JPopupMenu> menu) {
        c.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) menu.get().show(c, e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) menu.get().show(c, e.getX(), e.getY());
            }
        });
    }
}
