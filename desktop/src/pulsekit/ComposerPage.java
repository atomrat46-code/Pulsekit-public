package pulsekit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import static pulsekit.Pulsekit.*;

/**
 * The Composer tab: building blocks (patterns, Fillerns, fills, silence) on a stack; parts
 * (Intro, Verse, Chorus...) made of blocks and other parts (subparts), each repeated; and the song
 * form (AABA, ABABCB, Verse–Pre-chorus–Chorus, 12-bar blues, or letters added by hand) playing a
 * part for each letter. To Song puts the form on the Song tab (Composer.java has the rules). A
 * block dragged onto a part is added to it.
 */
final class ComposerPage {
    final Pulsekit app;
    Composer composer;
    JPanel body;
    /** The menu shown last (for the tests). */
    JPopupMenu lastMenu;

    ComposerPage(Pulsekit app) {
        this.app = app;
    }

    Component buildComposerPage() {
        this.body = new JPanel();
        this.body.setName("composer");
        this.body.setBackground(BG);
        this.body.setLayout(new BoxLayout(this.body, BoxLayout.Y_AXIS));
        this.body.setBorder(BorderFactory.createEmptyBorder(8, 4, 24, 4));
        JScrollPane scroll = new JScrollPane(this.body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    Composer composer() {
        if (this.composer == null) this.composer = Composer.load(PromptDb.dir());
        return this.composer;
    }

    void changed() {
        this.composer().save(PromptDb.dir());
        this.refresh();
    }

    JLabel label(String s, int size, boolean bold, Color color) {
        JLabel l = new JLabel(s);
        l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        l.setAlignmentX(0f);
        l.setBorder(BorderFactory.createEmptyBorder(8, 2, 4, 2));
        return l;
    }

    JPanel flow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4)) {
            // As tall as its rows, as wide as the page.
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, this.getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.setAlignmentX(0f);
        return p;
    }

    void refresh() {
        if (this.body == null) return;
        Composer c = this.composer();
        this.body.removeAll();
        this.body.add(this.label("Composer", 20, true, FG));
        this.body.add(this.label("<html>Put patterns, Fillerns and fills on the stack, make parts of them (a part can hold other parts), then a song form of parts. To Song puts it on the Song tab. Drag a block onto a part to add it.</html>", 12, false, MUTED));

        // Building blocks.
        this.body.add(this.label("BUILDING BLOCKS", 11, true, MUTED));
        JPanel stack = this.flow();
        stack.setName("composer-stack");
        for (int i = 0; i < c.stack.size(); i++) {
            final int at = i;
            final Composer.Block b = c.stack.get(i);
            final JButton chip = app.chip(b.label(), false);
            chip.setName("composer-block:" + b.name);
            chip.addActionListener(e -> this.blockMenu(chip, at));
            this.dragToParts(chip, at);
            stack.add(chip);
        }
        final JButton add = app.outline("+ Pattern, Fillern or fill", false);
        add.setName("composer-add-block");
        add.addActionListener(e -> app.songEditor.pickInto(add, b -> {
            this.composer().addBlock(b);
            this.changed();
            app.setNow("On the stack · " + b.label());
        }));
        stack.add(add);
        JButton silent = app.outline("+ Silent", false);
        silent.addActionListener(e -> {
            this.composer().addBlock(new Composer.Block(Composer.SILENT, "silent", "Silent"));
            this.changed();
        });
        stack.add(silent);
        this.body.add(stack);
        if (c.stack.isEmpty()) this.body.add(this.label("Nothing on the stack yet. A MIDI's right click in the Media browser can add its patterns too.", 12, false, MUTED));

        // Parts.
        this.body.add(this.label("PARTS", 11, true, MUTED));
        for (Composer.Part p : c.parts) this.body.add(this.partCard(p));
        JPanel np = this.flow();
        final JButton newPart = app.outline("+ New part", false);
        newPart.setName("composer-new-part");
        newPart.addActionListener(e -> this.newPart(newPart, null));
        np.add(newPart);
        this.body.add(np);

        // Song form.
        this.body.add(this.label("SONG FORM", 11, true, MUTED));
        JPanel forms = this.flow();
        for (final String[] f : Composer.FORMS) {
            JButton b = app.chip(f[0], f[0].equals(c.formName));
            b.setName("composer-form:" + f[0]);
            b.addActionListener(e -> {
                this.composer().applyForm(f[0]);
                this.changed();
                app.setNow(f[0] + " · " + Composer.formNote(f[0]));
            });
            forms.add(b);
        }
        this.body.add(forms);
        if (!c.formName.isEmpty()) this.body.add(this.label(Composer.formNote(c.formName), 12, false, MUTED));
        JPanel letters = this.flow();
        letters.setName("composer-letters");
        for (int i = 0; i < c.form.size(); i++) {
            final int at = i;
            String l = c.form.get(i);
            String part = c.letters.get(l);
            final JButton chip = app.chip(l + " · " + (part == null || part.isEmpty() ? "no part" : part), false);
            chip.setName("composer-letter:" + i);
            chip.addActionListener(e -> this.letterMenu(chip, at));
            letters.add(chip);
        }
        final JButton addLetter = app.outline("+ Letter", false);
        addLetter.setName("composer-add-letter");
        addLetter.addActionListener(e -> this.addLetter(addLetter));
        letters.add(addLetter);
        this.body.add(letters);
        JPanel go = this.flow();
        JButton toSong = app.action("To Song", HIT, BG, () -> this.toSong(true));
        toSong.setName("composer-to-song");
        JButton addSong = app.action("Add to Song", ELEV, FG, () -> this.toSong(false));
        addSong.setName("composer-add-to-song");
        go.add(toSong);
        go.add(addSong);
        this.body.add(go);
        this.body.add(Box.createVerticalGlue());
        this.body.revalidate();
        this.body.repaint();
    }

    /** A part: its name (a click: Rename, Add to Song, Delete), its entries (a click: more, fewer, move, remove), + Block, + Part. */
    JPanel partCard(final Composer.Part p) {
        JPanel card = new JPanel(new BorderLayout(0, 2)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, this.getPreferredSize().height);
            }
        };
        card.setName("composer-part:" + p.name);
        card.setBackground(SURFACE);
        card.setAlignmentX(0f);
        // A strip of the page's colour between cards.
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(4, 0, 4, 0, BG), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        final JButton name = app.chip(p.name, true);
        name.setName("composer-part-name:" + p.name);
        name.addActionListener(e -> this.partMenu(name, p));
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        head.setOpaque(false);
        head.add(name);
        card.add(head, BorderLayout.NORTH);
        JPanel entries = this.flow();
        for (int i = 0; i < p.entries.size(); i++) {
            final int at = i;
            final JButton chip = app.chip(p.entries.get(i).label(), p.entries.get(i).block == null);
            chip.setName("composer-entry:" + p.name + ":" + i);
            chip.addActionListener(e -> this.entryMenu(chip, p, at));
            entries.add(chip);
        }
        final JButton addBlock = app.outline("+ Block", false);
        addBlock.setName("composer-part-add:" + p.name);
        addBlock.addActionListener(e -> this.addBlockTo(addBlock, p));
        entries.add(addBlock);
        final JButton addSub = app.outline("+ Part", false);
        addSub.setName("composer-part-sub:" + p.name);
        addSub.addActionListener(e -> this.addSubTo(addSub, p));
        entries.add(addSub);
        card.add(entries, BorderLayout.CENTER);
        return card;
    }

    /** A block's chip: dragged and let go over a part's card, the block is added to that part. */
    void dragToParts(final JButton chip, final int at) {
        chip.addMouseListener(new MouseAdapter() {
            Point from;

            @Override
            public void mousePressed(MouseEvent e) {
                this.from = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (this.from == null || this.from.distance(e.getPoint()) < 12) return;
                Point at2 = SwingUtilities.convertPoint(chip, e.getPoint(), ComposerPage.this.body);
                Component under = SwingUtilities.getDeepestComponentAt(ComposerPage.this.body, at2.x, at2.y);
                while (under != null && (under.getName() == null || !under.getName().startsWith("composer-part:"))) under = under.getParent();
                if (under == null) return;
                Composer.Part p = ComposerPage.this.composer().part(under.getName().substring("composer-part:".length()));
                if (p != null && at < ComposerPage.this.composer().stack.size()) ComposerPage.this.addEntry(p, Composer.Entry.of(ComposerPage.this.composer().stack.get(at)));
            }
        });
    }

    /** A menu of `labels` under `anchor`, each running its `runs`. */
    JPopupMenu menu(Component anchor, List<String> labels, List<Runnable> runs) {
        JPopupMenu m = new JPopupMenu();
        m.setName("composer-menu");
        for (int i = 0; i < labels.size(); i++) {
            final Runnable r = runs.get(i);
            JMenuItem it = new JMenuItem(labels.get(i));
            it.setName("composer-menu:" + labels.get(i));
            it.addActionListener(e -> r.run());
            m.add(it);
        }
        this.lastMenu = m;
        if (anchor != null && anchor.isShowing()) m.show(anchor, 0, anchor.getHeight());
        return m;
    }

    String ask(String title, String now) {
        Object v = JOptionPane.showInputDialog(app, title, "Composer", JOptionPane.PLAIN_MESSAGE, null, null, now == null ? "" : now);
        return v == null ? null : v.toString();
    }

    void blockMenu(Component anchor, final int at) {
        final Composer c = this.composer();
        if (at < 0 || at >= c.stack.size()) return;
        final Composer.Block b = c.stack.get(at);
        List<String> labels = new ArrayList<String>();
        List<Runnable> runs = new ArrayList<Runnable>();
        for (final Composer.Part p : c.parts) {
            labels.add("Add to " + p.name);
            runs.add(() -> this.addEntry(p, Composer.Entry.of(b)));
        }
        labels.add("Add to a new part…");
        runs.add(() -> this.newPart(anchor, b));
        labels.add("Remove from the stack");
        runs.add(() -> {
            c.removeBlock(at);
            this.changed();
        });
        this.menu(anchor, labels, runs);
    }

    void addEntry(Composer.Part p, Composer.Entry e) {
        p.entries.add(e);
        this.changed();
        app.setNow(p.name + " · " + e.label());
    }

    void addBlockTo(final Component anchor, final Composer.Part p) {
        final Composer c = this.composer();
        List<String> labels = new ArrayList<String>();
        List<Runnable> runs = new ArrayList<Runnable>();
        for (final Composer.Block b : c.stack) {
            labels.add(b.label());
            runs.add(() -> this.addEntry(p, Composer.Entry.of(b)));
        }
        labels.add("Another pattern, Fillern or fill…");
        runs.add(() -> app.songEditor.pickInto(anchor, b -> this.addEntry(p, Composer.Entry.of(this.composer().addBlock(b)))));
        this.menu(anchor, labels, runs);
    }

    void addSubTo(Component anchor, final Composer.Part p) {
        final Composer c = this.composer();
        List<String> labels = new ArrayList<String>();
        List<Runnable> runs = new ArrayList<Runnable>();
        for (final Composer.Part q : c.parts) {
            if (!c.canNest(p, q)) continue;
            labels.add(q.name);
            runs.add(() -> this.addEntry(p, Composer.Entry.sub(q.name)));
        }
        if (labels.isEmpty()) {
            app.setNow("No part can go inside " + p.name + " (make another part first; a part cannot hold itself)");
            return;
        }
        this.menu(anchor, labels, runs);
    }

    void entryMenu(Component anchor, final Composer.Part p, final int at) {
        if (at < 0 || at >= p.entries.size()) return;
        final Composer.Entry e = p.entries.get(at);
        List<String> labels = new ArrayList<String>();
        List<Runnable> runs = new ArrayList<Runnable>();
        labels.add("More (×" + (e.repeats + 1) + ")");
        runs.add(() -> e.repeats = Math.min(64, e.repeats + 1));
        if (e.repeats > 1) {
            labels.add("Fewer (×" + (e.repeats - 1) + ")");
            runs.add(() -> e.repeats = e.repeats - 1);
        }
        if (at > 0) {
            labels.add("Move earlier");
            runs.add(() -> java.util.Collections.swap(p.entries, at, at - 1));
        }
        if (at + 1 < p.entries.size()) {
            labels.add("Move later");
            runs.add(() -> java.util.Collections.swap(p.entries, at, at + 1));
        }
        labels.add("Remove");
        runs.add(() -> p.entries.remove(at));
        List<Runnable> thenChanged = new ArrayList<Runnable>();
        for (final Runnable r : runs) thenChanged.add(() -> {
            r.run();
            this.changed();
        });
        this.menu(anchor, labels, thenChanged);
    }

    /** + New part: a name from the usual ones, or typed; `first` is put in it when given. */
    void newPart(Component anchor, final Composer.Block first) {
        List<String> labels = new ArrayList<String>();
        List<Runnable> runs = new ArrayList<Runnable>();
        for (final String n : Composer.PART_NAMES) {
            labels.add(n);
            runs.add(() -> this.madePart(n, first));
        }
        labels.add("Other name…");
        runs.add(() -> {
            String n = this.ask("New part", "");
            if (n != null && !n.trim().isEmpty()) this.madePart(n, first);
        });
        this.menu(anchor, labels, runs);
    }

    void madePart(String name, Composer.Block first) {
        Composer.Part p = this.composer().addPart(name);
        if (first != null) p.entries.add(Composer.Entry.of(first));
        this.changed();
        app.setNow("New part · " + p.name);
    }

    void partMenu(Component anchor, final Composer.Part p) {
        List<String> labels = new ArrayList<String>();
        List<Runnable> runs = new ArrayList<Runnable>();
        labels.add("Rename");
        runs.add(() -> {
            String n = this.ask("Rename " + p.name, p.name);
            if (n == null) return;
            if (!this.composer().renamePart(p, n)) app.setNow("There is a part named " + n.trim() + " already");
            this.changed();
        });
        labels.add("Add to Song");
        runs.add(() -> app.songEditor.addComposed(this.composer().renderPart(p.name, app.songEditor::partsFor), p.name));
        labels.add("Delete");
        runs.add(() -> {
            this.composer().deletePart(p);
            this.changed();
        });
        this.menu(anchor, labels, runs);
    }

    /** + Letter: a letter used already (its part again), or the next new one (its part picked). */
    void addLetter(final Component anchor) {
        final Composer c = this.composer();
        List<String> labels = new ArrayList<String>();
        List<Runnable> runs = new ArrayList<Runnable>();
        for (final String l : new ArrayList<String>(c.letters.keySet())) {
            labels.add(l + " · " + (c.letters.get(l).isEmpty() ? "no part" : c.letters.get(l)));
            runs.add(() -> {
                c.form.add(l);
                this.changed();
            });
        }
        final String next = c.nextLetter();
        labels.add("New letter " + next);
        runs.add(() -> {
            c.form.add(next);
            c.letters.put(next, "");
            this.changed();
            this.assignLetter(anchor, next);
        });
        this.menu(anchor, labels, runs);
    }

    void letterMenu(final Component anchor, final int at) {
        final Composer c = this.composer();
        if (at < 0 || at >= c.form.size()) return;
        final String l = c.form.get(at);
        List<String> labels = new ArrayList<String>();
        List<Runnable> runs = new ArrayList<Runnable>();
        labels.add("Plays part…");
        runs.add(() -> this.assignLetter(anchor, l));
        labels.add("Remove this " + l);
        runs.add(() -> {
            c.form.remove(at);
            if (!c.form.contains(l)) c.letters.remove(l);
            this.changed();
        });
        this.menu(anchor, labels, runs);
    }

    /** Which part a letter plays (every place the letter is). */
    void assignLetter(Component anchor, final String l) {
        final Composer c = this.composer();
        List<String> labels = new ArrayList<String>();
        List<Runnable> runs = new ArrayList<Runnable>();
        for (final Composer.Part p : c.parts) {
            labels.add(p.name);
            runs.add(() -> {
                c.letters.put(l, p.name);
                this.changed();
            });
        }
        labels.add("New part…");
        runs.add(() -> {
            String n = this.ask("New part for " + l, "");
            if (n == null || n.trim().isEmpty()) return;
            Composer.Part p = c.addPart(n);
            c.letters.put(l, p.name);
            this.changed();
        });
        // The letter's chip is gone after a refresh: the menu comes under the letters row.
        Component at = anchor != null && anchor.isShowing() ? anchor : null;
        this.menu(at, labels, runs);
    }

    /** To Song (in place of the Song tab's song) or Add to Song (after it): the form's parts, then the Song tab. */
    void toSong(boolean replace) {
        Composer c = this.composer();
        List<Engine.Part> parts = c.renderForm(app.songEditor::partsFor);
        if (parts.isEmpty()) {
            app.setNow("The song form plays nothing yet: give its letters parts with blocks in them");
            return;
        }
        app.songLane = "original";
        if (replace) app.song.clear();
        app.showView("song");
        app.songEditor.addComposed(parts, c.formName.isEmpty() ? "the song form" : c.formName);
    }
}
