package pulsekit;

import android.app.AlertDialog;
import android.content.ClipData;
import android.view.DragEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

import static pulsekit.MainActivity.*;

/**
 * The Composer tab: building blocks (patterns, Fillerns, fills, silence) on a stack; parts
 * (Intro, Verse, Chorus...) made of blocks and other parts (subparts), each repeated; and the song
 * form (AABA, ABABCB, Verse–Pre-chorus–Chorus, 12-bar blues, or letters added by hand) playing a
 * part for each letter. To Song puts the form on the Song tab (Composer.java has the rules). A
 * block's long press drags it onto a part.
 */
final class ComposerPage {
    final MainActivity app;
    Composer composer;
    LinearLayout pane;
    LinearLayout body;
    /** The dialog shown last (for the tests). */
    static AlertDialog lastDialog;
    /** Drags start from a block's long press (off under Robolectric, where a drag never ends). */
    static boolean drags = true;

    ComposerPage(MainActivity app) {
        this.app = app;
    }

    void build(FrameLayout frame) {
        this.pane = this.app.col();
        this.pane.setTag("composer");
        this.pane.setVisibility(8);
        ScrollView scroll = new ScrollView(this.app);
        this.body = this.app.col();
        this.body.setPadding(0, this.app.dp(4), 0, this.app.dp(24));
        scroll.addView(this.body);
        this.pane.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        frame.addView(this.pane);
    }

    Composer composer() {
        if (this.composer == null) this.composer = Composer.load(this.app.getFilesDir());
        return this.composer;
    }

    void changed() {
        this.composer().save(this.app.getFilesDir());
        this.refresh();
    }

    void refresh() {
        if (this.body == null) return;
        Composer c = this.composer();
        this.body.removeAllViews();
        this.body.addView(this.app.text("Composer", 18, true));
        this.body.addView(this.app.hint("Put patterns, Fillerns and fills on the stack, make parts of them (a part can hold other parts), then a song form of parts. To Song puts it on the Song tab."));

        // Building blocks.
        this.body.addView(this.app.sectionLabel("Building blocks"));
        FlowLayout stack = new FlowLayout(this.app, 0, 0);
        stack.setTag("composer-stack");
        for (int i = 0; i < c.stack.size(); i++) {
            final int at = i;
            final Composer.Block b = c.stack.get(i);
            TextView chip = this.app.pill(b.label(), false, v -> this.blockMenu(at));
            chip.setTag("composer-block:" + b.name);
            chip.setOnLongClickListener(v -> {
                if (!drags) return false;
                v.startDragAndDrop(ClipData.newPlainText("block", Integer.toString(at)), new View.DragShadowBuilder(v), null, 0);
                return true;
            });
            stack.addView(chip);
        }
        TextView add = this.app.outline("+ Pattern, Fillern or fill", false, v -> this.app.songEditor.pickInto(b -> {
            this.composer().addBlock(b);
            this.changed();
            this.app.setNow("On the stack · " + b.label());
        }));
        add.setTag("composer-add-block");
        stack.addView(add);
        TextView silent = this.app.outline("+ Silent", false, v -> {
            this.composer().addBlock(new Composer.Block(Composer.SILENT, "silent", "Silent"));
            this.changed();
        });
        stack.addView(silent);
        this.body.addView(stack, new LinearLayout.LayoutParams(-1, -2));
        if (c.stack.isEmpty()) this.body.addView(this.app.hint("Nothing on the stack yet. A MIDI's long press in the Media browser can add its patterns too."));

        // Parts.
        this.body.addView(this.app.sectionLabel("Parts"));
        for (final Composer.Part p : c.parts) this.body.addView(this.partCard(p));
        TextView newPart = this.app.outline("+ New part", false, v -> this.newPart(null));
        newPart.setTag("composer-new-part");
        this.body.addView(newPart);

        // Song form.
        this.body.addView(this.app.sectionLabel("Song form"));
        FlowLayout forms = new FlowLayout(this.app, 0, 0);
        for (final String[] f : Composer.FORMS) {
            TextView b = this.app.pill(f[0], f[0].equals(c.formName), v -> {
                this.composer().applyForm(f[0]);
                this.changed();
                this.app.setNow(f[0] + " · " + Composer.formNote(f[0]));
            });
            b.setTag("composer-form:" + f[0]);
            forms.addView(b);
        }
        this.body.addView(forms, new LinearLayout.LayoutParams(-1, -2));
        if (!c.formName.isEmpty()) this.body.addView(this.app.hint(Composer.formNote(c.formName)));
        FlowLayout letters = new FlowLayout(this.app, 0, 0);
        letters.setTag("composer-letters");
        for (int i = 0; i < c.form.size(); i++) {
            final int at = i;
            String l = c.form.get(i);
            String part = c.letters.get(l);
            TextView chip = this.app.pill(l + " · " + (part == null || part.isEmpty() ? "no part" : part), false, v -> this.letterMenu(at));
            chip.setTag("composer-letter:" + i);
            letters.addView(chip);
        }
        TextView addLetter = this.app.outline("+ Letter", false, v -> this.addLetter());
        addLetter.setTag("composer-add-letter");
        letters.addView(addLetter);
        this.body.addView(letters, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout go = this.app.row();
        go.setPadding(0, this.app.dp(8), 0, 0);
        TextView toSong = this.app.action("To Song", HIT, BG, v -> this.toSong(true));
        toSong.setTag("composer-to-song");
        TextView addSong = this.app.action("Add to Song", ELEV, FG, v -> this.toSong(false));
        addSong.setTag("composer-add-to-song");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, this.app.dp(44), 1f);
        lp.setMargins(0, 0, this.app.dp(8), 0);
        go.addView(toSong, lp);
        go.addView(addSong, new LinearLayout.LayoutParams(0, this.app.dp(44), 1f));
        this.body.addView(go);
    }

    /** A part: its name (a tap: Rename, Delete), its entries (a tap: more, fewer, move, remove), + Block, + Part. Blocks dropped on it are added. */
    View partCard(final Composer.Part p) {
        LinearLayout card = this.app.col();
        card.setTag("composer-part:" + p.name);
        card.setPadding(this.app.dp(8), this.app.dp(6), this.app.dp(8), this.app.dp(6));
        card.setBackground(this.app.round(SURFACE, 10));
        TextView name = this.app.text(p.name, 15, true);
        name.setTag("composer-part-name:" + p.name);
        name.setPadding(this.app.dp(4), this.app.dp(4), 0, this.app.dp(4));
        name.setOnClickListener(v -> this.partMenu(p));
        card.addView(name);
        FlowLayout entries = new FlowLayout(this.app, 0, 0);
        for (int i = 0; i < p.entries.size(); i++) {
            final int at = i;
            TextView chip = this.app.pill(p.entries.get(i).label(), p.entries.get(i).block == null, v -> this.entryMenu(p, at));
            chip.setTag("composer-entry:" + p.name + ":" + i);
            entries.addView(chip);
        }
        TextView addBlock = this.app.outline("+ Block", false, v -> this.addBlockTo(p));
        addBlock.setTag("composer-part-add:" + p.name);
        entries.addView(addBlock);
        TextView addSub = this.app.outline("+ Part", false, v -> this.addSubTo(p));
        addSub.setTag("composer-part-sub:" + p.name);
        entries.addView(addSub);
        card.addView(entries, new LinearLayout.LayoutParams(-1, -2));
        card.setOnDragListener((v, e) -> {
            if (e.getAction() == DragEvent.ACTION_DRAG_STARTED) return e.getClipDescription() != null && e.getClipDescription().hasMimeType("text/plain");
            if (e.getAction() != DragEvent.ACTION_DROP) return true;
            try {
                int at = Integer.parseInt(String.valueOf(e.getClipData().getItemAt(0).getText()));
                this.addEntry(p, Composer.Entry.of(this.composer().stack.get(at)));
            } catch (Exception ignored) {
                return false;
            }
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, this.app.dp(4), 0, this.app.dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    AlertDialog list(String title, List<String> items, final java.util.function.IntConsumer picked) {
        lastDialog = new AlertDialog.Builder(this.app).setTitle(title)
            .setItems(items.toArray(new CharSequence[0]), (d, w) -> picked.accept(w))
            .setNegativeButton("Cancel", null).show();
        return lastDialog;
    }

    void ask(String title, String now, final java.util.function.Consumer<String> done) {
        final EditText field = new EditText(this.app);
        field.setSingleLine(true);
        field.setText(now == null ? "" : now);
        field.setTag("composer-ask");
        lastDialog = new AlertDialog.Builder(this.app).setTitle(title).setView(field)
            .setPositiveButton("OK", (d, w) -> done.accept(field.getText().toString()))
            .setNegativeButton("Cancel", null).show();
    }

    void blockMenu(final int at) {
        final Composer c = this.composer();
        if (at < 0 || at >= c.stack.size()) return;
        final Composer.Block b = c.stack.get(at);
        List<String> items = new ArrayList<String>();
        final List<Runnable> runs = new ArrayList<Runnable>();
        for (final Composer.Part p : c.parts) {
            items.add("Add to " + p.name);
            runs.add(() -> this.addEntry(p, Composer.Entry.of(b)));
        }
        items.add("Add to a new part…");
        runs.add(() -> this.newPart(b));
        items.add("Remove from the stack");
        runs.add(() -> {
            c.removeBlock(at);
            this.changed();
        });
        this.list(b.label(), items, w -> runs.get(w).run());
    }

    void addEntry(Composer.Part p, Composer.Entry e) {
        p.entries.add(e);
        this.changed();
        this.app.setNow(p.name + " · " + e.label());
    }

    void addBlockTo(final Composer.Part p) {
        final Composer c = this.composer();
        if (c.stack.isEmpty()) {
            // Nothing on the stack: picked straight from the patterns, and kept on the stack too.
            this.app.songEditor.pickInto(b -> this.addEntry(p, Composer.Entry.of(this.composer().addBlock(b))));
            return;
        }
        List<String> items = new ArrayList<String>();
        for (Composer.Block b : c.stack) items.add(b.label());
        items.add("Another pattern, Fillern or fill…");
        this.list("Add to " + p.name, items, w -> {
            if (w < c.stack.size()) this.addEntry(p, Composer.Entry.of(c.stack.get(w)));
            else this.app.songEditor.pickInto(b -> this.addEntry(p, Composer.Entry.of(this.composer().addBlock(b))));
        });
    }

    void addSubTo(final Composer.Part p) {
        final Composer c = this.composer();
        final List<Composer.Part> can = new ArrayList<Composer.Part>();
        for (Composer.Part q : c.parts) if (c.canNest(p, q)) can.add(q);
        if (can.isEmpty()) {
            this.app.setNow("No part can go inside " + p.name + " (make another part first; a part cannot hold itself)");
            return;
        }
        List<String> items = new ArrayList<String>();
        for (Composer.Part q : can) items.add(q.name);
        this.list("A part inside " + p.name, items, w -> this.addEntry(p, Composer.Entry.sub(can.get(w).name)));
    }

    void entryMenu(final Composer.Part p, final int at) {
        if (at < 0 || at >= p.entries.size()) return;
        final Composer.Entry e = p.entries.get(at);
        List<String> items = new ArrayList<String>();
        final List<Runnable> runs = new ArrayList<Runnable>();
        items.add("More (×" + (e.repeats + 1) + ")");
        runs.add(() -> e.repeats = Math.min(64, e.repeats + 1));
        if (e.repeats > 1) {
            items.add("Fewer (×" + (e.repeats - 1) + ")");
            runs.add(() -> e.repeats = e.repeats - 1);
        }
        if (at > 0) {
            items.add("Move earlier");
            runs.add(() -> java.util.Collections.swap(p.entries, at, at - 1));
        }
        if (at + 1 < p.entries.size()) {
            items.add("Move later");
            runs.add(() -> java.util.Collections.swap(p.entries, at, at + 1));
        }
        items.add("Remove");
        runs.add(() -> p.entries.remove(at));
        this.list(e.label(), items, w -> {
            runs.get(w).run();
            this.changed();
        });
    }

    /** + New part: a name from the usual ones, or typed; `first` is put in it when given. */
    void newPart(final Composer.Block first) {
        final List<String> items = new ArrayList<String>(java.util.Arrays.asList(Composer.PART_NAMES));
        items.add("Other name…");
        this.list("New part", items, w -> {
            if (w < Composer.PART_NAMES.length) this.madePart(Composer.PART_NAMES[w], first);
            else this.ask("New part", "", n -> this.madePart(n, first));
        });
    }

    void madePart(String name, Composer.Block first) {
        Composer.Part p = this.composer().addPart(name);
        if (first != null) p.entries.add(Composer.Entry.of(first));
        this.changed();
        this.app.setNow("New part · " + p.name);
    }

    void partMenu(final Composer.Part p) {
        List<String> items = new ArrayList<String>();
        final List<Runnable> runs = new ArrayList<Runnable>();
        items.add("Rename");
        runs.add(() -> this.ask("Rename " + p.name, p.name, n -> {
            if (!this.composer().renamePart(p, n)) this.app.setNow("There is a part named " + n.trim() + " already");
            this.changed();
        }));
        items.add("Add to Song");
        runs.add(() -> this.app.songEditor.addComposed(this.composer().renderPart(p.name, this.app.songEditor::partsFor), p.name));
        items.add("Delete");
        runs.add(() -> {
            this.composer().deletePart(p);
            this.changed();
        });
        this.list(p.name, items, w -> runs.get(w).run());
    }

    /** + Letter: a letter used already (its part again), or the next new one (its part picked). */
    void addLetter() {
        final Composer c = this.composer();
        final List<String> used = new ArrayList<String>(c.letters.keySet());
        List<String> items = new ArrayList<String>();
        for (String l : used) items.add(l + " · " + (c.letters.get(l).isEmpty() ? "no part" : c.letters.get(l)));
        final String next = c.nextLetter();
        items.add("New letter " + next);
        this.list("+ Letter", items, w -> {
            if (w < used.size()) {
                c.form.add(used.get(w));
                this.changed();
                return;
            }
            c.form.add(next);
            c.letters.put(next, "");
            this.changed();
            this.assignLetter(next);
        });
    }

    void letterMenu(final int at) {
        final Composer c = this.composer();
        if (at < 0 || at >= c.form.size()) return;
        final String l = c.form.get(at);
        List<String> items = new ArrayList<String>();
        final List<Runnable> runs = new ArrayList<Runnable>();
        items.add("Plays part…");
        runs.add(() -> this.assignLetter(l));
        items.add("Remove this " + l);
        runs.add(() -> {
            c.form.remove(at);
            if (!c.form.contains(l)) c.letters.remove(l);
            this.changed();
        });
        this.list(l + " · " + (c.letters.get(l) == null || c.letters.get(l).isEmpty() ? "no part" : c.letters.get(l)), items, w -> runs.get(w).run());
    }

    /** Which part a letter plays (every place the letter is). */
    void assignLetter(final String l) {
        final Composer c = this.composer();
        List<String> items = new ArrayList<String>();
        for (Composer.Part p : c.parts) items.add(p.name);
        items.add("New part…");
        this.list(l + " plays", items, w -> {
            if (w < c.parts.size()) {
                c.letters.put(l, c.parts.get(w).name);
                this.changed();
            } else {
                this.ask("New part for " + l, "", n -> {
                    Composer.Part p = c.addPart(n);
                    c.letters.put(l, p.name);
                    this.changed();
                });
            }
        });
    }

    /** To Song (in place of the Song tab's song) or Add to Song (after it): the form's parts, then the Song tab. */
    void toSong(boolean replace) {
        Composer c = this.composer();
        List<Engine.Part> parts = c.renderForm(this.app.songEditor::partsFor);
        if (parts.isEmpty()) {
            this.app.setNow("The song form plays nothing yet: give its letters parts with blocks in them");
            return;
        }
        this.app.songLane = "original";
        if (replace) this.app.song.clear();
        this.app.show("song");
        this.app.songEditor.addComposed(parts, c.formName.isEmpty() ? "the song form" : c.formName);
    }
}
