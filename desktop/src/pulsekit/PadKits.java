package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * The Pads tab's drum sets and kits (as the phone's): the sets in a row with + (a new one), a set's menu
 * (Rename, Copy into new, Match Original, Match empty from Original, Delete), and Add SF2… with the kits
 * added in a drop-down list: one picked loads into the set selected. Sets and kits are kept.
 */
final class PadKits {
    final Pulsekit app;
    JComboBox<String> kitList;
    final List<KitShelf.Kit> listed = new ArrayList<KitShelf.Kit>();
    boolean quiet;
    /** Tests: the answer a name question or a confirmation gets (null: asked), and the file Add SF2 takes. */
    static String answer;
    static Boolean sure;
    static File addFile;
    /** The kit loading now (the tests wait for it). */
    static volatile Thread loading;

    PadKits(Pulsekit app) {
        this.app = app;
    }

    static File setsDir() {
        return new File(new File(System.getProperty("user.home"), ".pulsekit"), "drumsets");
    }

    static File kitsDir() {
        return new File(new File(System.getProperty("user.home"), ".pulsekit"), "kits");
    }

    JPanel kitRow() {
        JPanel row = new JPanel(new java.awt.FlowLayout(0, 6, 4));
        row.setOpaque(false);
        row.setAlignmentX(0.0f);
        JButton add = app.outline("Add SF2…", false);
        add.setName("pads-add-kit");
        add.setToolTipText("A SoundFont (.sf2) or an SFZ kit (.sfz, or a .zip / .tar.gz with one) into the list; it loads into the drum set selected");
        add.addActionListener(e -> this.add());
        row.add(add);
        this.kitList = new JComboBox<String>();
        this.kitList.setName("pads-kit-list");
        this.kitList.setToolTipText("The kits added: one picked loads into the drum set selected (right-click Add SF2…: remove one)");
        this.kitList.addActionListener(e -> {
            int i = this.kitList.getSelectedIndex() - this.offset;
            if (this.quiet || i < 0 || i >= this.listed.size()) return;
            this.load(this.listed.get(i));
        });
        app.onChipMenu(add, this::kitMenu);
        // Add zip: a drum set from a zip (DrumMidi --make-set's or --combine-sets' set folders, as shared).
        JButton zip = app.outline("Add zip\u2026", false);
        zip.setName("pads-add-zip");
        zip.setToolTipText("A drum set from a zip of drum sets made from songs (DrumMidi's <song>-set folders): a new drum set on the pads; a zip of an SFZ kit goes onto the list as Add SF2 does");
        zip.addActionListener(e -> this.addZip());
        row.add(zip);
        row.add(this.kitList);
        this.refreshKits();
        // A SoundFont the pads have from before (File, or an earlier version): on the list as Original's kit.
        Engine.DrumSet original = app.drumSets.isEmpty() ? null : app.drumSets.get(0);
        if (original != null && original.original && (original.kit == null || original.kit.isEmpty())
            && new File(new File(System.getProperty("user.home"), ".pulsekit"), "kit.sf2").isFile()) this.adoptLoaded(null);
        return row;
    }

    /** Whether Original's lost kit was looked for this run (the kit added last loaded into it). */
    boolean recentTried;

    /** 1 while the list starts with "Kit…" (a set without a kit), else 0. */
    int offset;

    /**
     * The list again: Default kit (Pulsekit's own sounds), then the kits added; the selected set's kit
     * selected (Original without one: Default kit; another set without one: "Kit…" first).
     */
    void refreshKits() {
        if (this.kitList == null) return;
        this.quiet = true;
        this.listed.clear();
        KitShelf.Kit def = new KitShelf.Kit();
        def.name = KitShelf.DEFAULT;
        this.listed.add(def);
        this.listed.addAll(KitShelf.list(kitsDir()));
        Engine.DrumSet set = app.playback.activeDrumSet();
        String cur = set.kit == null || set.kit.isEmpty() ? (set.original ? KitShelf.DEFAULT : "") : set.kit;
        // Original without a kit and without its saved SoundFont (lost): the kit added last loaded into it, once.
        if (set.original && (set.kit == null || set.kit.isEmpty()) && !this.recentTried && !new File(new File(System.getProperty("user.home"), ".pulsekit"), "kit.sf2").isFile()) {
            this.recentTried = true;
            KitShelf.Kit recent = KitShelf.mostRecent(kitsDir());
            if (recent != null) {
                this.load(recent);
                cur = recent.name;
            }
        }
        // Original playing sounds that are not Pulsekit's own, with no kit named: not Default kit.
        if (set.original && KitShelf.DEFAULT.equals(cur) && !KitShelf.ownSounds(app.voices)) cur = "";
        this.offset = cur.isEmpty() ? 1 : 0;
        String top = set.original ? KitShelf.UNKNOWN : "Kit\u2026";
        this.kitList.removeAllItems();
        if (this.offset == 1) this.kitList.addItem(top);
        int at = 0;
        for (int i = 0; i < this.listed.size(); i++) {
            this.kitList.addItem(this.listed.get(i).name);
            if (this.listed.get(i).name.equals(cur)) at = i + this.offset;
        }
        this.kitList.setSelectedIndex(at);
        this.quiet = false;
    }

    /**
     * The pads' own kit (kit.sf2: a SoundFont or SFZ kit loaded from File, or before the list was there)
     * onto the list, as Original's kit; `name`: the file's name (null: the SoundFont's own).
     */
    void adoptLoaded(final String name) {
        final File kit = new File(new File(System.getProperty("user.home"), ".pulsekit"), "kit.sf2");
        loading = new Thread(() -> {
            final String got = KitShelf.adopt(kitsDir(), kit, name);
            if (got == null) return;
            SwingUtilities.invokeLater(() -> {
                for (Engine.DrumSet d : app.drumSets) if (d.original) d.kit = got;
                this.saveSets();
                this.refreshKits();
            });
        }, "pulsekit-kit-adopt");
        loading.start();
    }

    void add() {
        File f = addFile;
        addFile = null;
        if (f == null) {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Add a SoundFont or an SFZ kit");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SoundFonts and SFZ kits (.sf2, .sfz, .zip, .tar, .tar.gz)", "sf2", "sfz", "zip", "tar", "gz", "tgz"));
            if (fc.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
            f = fc.getSelectedFile();
        }
        final File file = f;
        app.setNow("Adding " + file.getName() + "…");
        loading = new Thread(() -> {
            try {
                final List<KitShelf.Kit> kits = KitShelf.add(kitsDir(), file, file.getName());
                SwingUtilities.invokeLater(() -> {
                    this.refreshKits();
                    app.setNow(kits.size() == 1 ? "Added " + kits.get(0).name : "Added " + kits.size() + " kits from " + file.getName());
                    this.load(kits.get(0));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> app.setNow("Could not add " + file.getName() + ": " + String.valueOf(ex.getMessage()).split("\n")[0]));
            }
        }, "pulsekit-kit-add");
        loading.start();
    }

    void load(final KitShelf.Kit kit) {
        final Engine.DrumSet set = app.playback.activeDrumSet();
        app.setNow("Loading " + kit.name + "…");
        loading = new Thread(() -> {
            try {
                final boolean def = KitShelf.DEFAULT.equals(kit.name) && kit.file.isEmpty();
                final short[][] v = def ? AudioIo.buildVoices(AudioIo.SR) : KitShelf.voices(kitsDir(), kit);
                final byte[] sf2 = set.original && !def ? AudioIo.encodeSf2(v, kit.name) : null;
                SwingUtilities.invokeLater(() -> {
                    int n = 0;
                    for (short[] x : v) if (x != null) n++;
                    if (set.original) {
                        for (int t = 0; t < app.voices.length && t < v.length; t++) if (v[t] != null) app.voices[t] = v[t];
                        app.playback.fillMissingVoices();
                        // Default kit: Pulsekit's own sounds again, no kit.sf2 at the next start either.
                        if (def) new File(new File(System.getProperty("user.home"), ".pulsekit"), "kit.sf2").delete();
                        else app.persistence.persistSf2(sf2);
                    } else {
                        DrumSetStore.fill(set, v, kit.name);
                    }
                    set.kit = kit.name;
                    app.playback.reloadDrumBank();
                    this.saveSets();
                    app.playback.refreshDrumSets();
                    app.setNow(set.name + " · " + kit.name + " · " + n + " drums");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    app.setNow("Could not load " + kit.name + ": " + String.valueOf(ex.getMessage()).split("\n")[0]);
                    this.refreshKits();
                });
            }
        }, "pulsekit-kit-load");
        loading.start();
    }

    /** Remove a kit from the list. */
    JPopupMenu kitMenu() {
        JPopupMenu m = new JPopupMenu();
        MenuCancel.add(m);
        for (final KitShelf.Kit k : KitShelf.list(kitsDir())) {
            JMenuItem it = new JMenuItem("Remove " + k.name + " from the list");
            it.addActionListener(e -> {
                if (!this.confirm("Remove " + k.name + " from the list? The drum sets keep their sounds.")) return;
                try {
                    KitShelf.remove(kitsDir(), k);
                } catch (Exception ignored) {
                    // listed as it was
                }
                this.refreshKits();
            });
            m.add(it);
        }
        return m;
    }

    void newSet() {
        String name = this.askName("New drum set", Engine.uniqueSetName("Drum set", app.drumSets));
        if (name == null) return;
        Engine.DrumSet s = Engine.DrumSet.isolated(null, Engine.uniqueSetName(name, app.drumSets));
        for (int t = 0; t < Engine.TRACK_ID.length; t++) s.matchOrig[t] = true;
        app.drumSets.add(s);
        this.select(app.drumSets.size() - 1);
        app.setNow(s.name + ": pick a kit for it");
    }

    void select(int i) {
        app.playback.drumSetIndex = i;
        app.playback.reloadDrumBank();
        this.saveSets();
        app.playback.refreshDrumSets();
    }

    JPopupMenu setMenu(final int i) {
        JPopupMenu m = new JPopupMenu();
        MenuCancel.add(m);
        if (i < 0 || i >= app.drumSets.size()) return m;
        final Engine.DrumSet s = app.drumSets.get(i);
        if (!s.original) {
            JMenuItem rename = new JMenuItem("Rename");
            rename.addActionListener(e -> {
                String name = this.askName("Rename drum set", s.name);
                if (name == null) return;
                List<Engine.DrumSet> others = new ArrayList<Engine.DrumSet>(app.drumSets);
                others.remove(s);
                s.name = Engine.uniqueSetName(name, others);
                this.select(i);
            });
            m.add(rename);
        }
        JMenuItem copy = new JMenuItem("Copy into new");
        copy.addActionListener(e -> {
            String name = this.askName("Copy into a new drum set", Engine.uniqueSetName(s.name + " copy", app.drumSets));
            if (name == null) return;
            Engine.DrumSet c = s.copyAs(Engine.uniqueSetName(name, app.drumSets));
            if (s.original) {
                for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                    c.samples[t] = app.voices[Engine.soundTrack(t)];
                    c.matchOrig[t] = c.samples[t] == null;
                }
            }
            app.drumSets.add(c);
            this.select(app.drumSets.size() - 1);
        });
        m.add(copy);
        if (!s.original) {
            JMenuItem matchAll = new JMenuItem("Match Original");
            matchAll.addActionListener(e -> {
                for (int t = 0; t < Engine.TRACK_ID.length; t++) s.matchOrig[t] = true;
                this.select(i);
                app.setNow(s.name + " matches Original");
            });
            JMenuItem match = new JMenuItem("Match empty from Original");
            match.addActionListener(e -> {
                for (int t = 0; t < Engine.TRACK_ID.length; t++) if (s.samples[t] == null) s.matchOrig[t] = true;
                this.select(i);
                app.setNow(s.name + ": empty pads match Original");
            });
            JMenuItem del = new JMenuItem("Delete");
            del.addActionListener(e -> {
                if (!this.confirm("Delete the drum set " + s.name + "?")) return;
                app.drumSets.remove(s);
                // A song set's hits go with it.
                java.io.File hits = DrumSetStore.hitsDir(setsDir(), s);
                java.io.File[] kept = hits.listFiles();
                if (kept != null) for (java.io.File f : kept) f.delete();
                hits.delete();
                int at = app.playback.drumSetIndex;
                if (at >= app.drumSets.size() || at == i) at = 0;
                else if (at > i) at--;
                this.select(at);
                app.setNow("Drum set " + s.name + " deleted");
            });
            m.add(matchAll);
            m.add(match);
            m.add(del);
        }
        return m;
    }

    String askName(String title, String suggested) {
        if (answer != null) {
            String a = answer.trim();
            answer = null;
            return a.isEmpty() ? null : a;
        }
        Object got = JOptionPane.showInputDialog(app, "Name:", title, JOptionPane.PLAIN_MESSAGE, null, null, suggested);
        String n = got == null ? "" : got.toString().trim();
        return n.isEmpty() ? null : n;
    }

    boolean confirm(String question) {
        if (sure != null) {
            boolean s = sure.booleanValue();
            sure = null;
            return s;
        }
        return JOptionPane.showConfirmDialog(app, question, "Pulsekit", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
    }

    /** What fromSong said last (for the tests). */
    static volatile String lastSongSet;

    /**
     * DrumMidi --make-set's drum set (its log names the folder): read now (the run's folder may be a temporary
     * one), then added as a new drum set named after the song, its drums on their pads and the other pads
     * as Original; selected, saved.
     */
    static void fromSong(final Pulsekit app, String log) {
        final java.io.File dir = SongDrumSet.folderIn(log);
        if (dir == null) return;
        fromFolder(app, dir, null);
    }

    /** Tests: the zip Add zip takes, and which of its sets is picked (null: asked). */
    static File zipFile;
    static Integer zipPick;

    /**
     * Add zip: the zip's drum sets (folders with set.txt: DrumMidi --make-set's, --combine-sets') unpacked; one
     * becomes a new drum set (several: which, a combined one first). A zip without them: an SFZ kit, as Add SF2.
     */
    void addZip() {
        File f = zipFile;
        zipFile = null;
        if (f == null) {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Add a drum set from a zip");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Zips (.zip)", "zip"));
            if (fc.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
            f = fc.getSelectedFile();
        }
        final File file = f;
        app.setNow("Reading " + file.getName() + "\u2026");
        loading = new Thread(() -> {
            final List<File> sets;
            try {
                File to = java.nio.file.Files.createTempDirectory("pulsekit-zip-set").toFile();
                sets = SongDrumSet.fromZip(file, to);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> app.setNow("Could not read " + file.getName() + ": " + String.valueOf(ex.getMessage()).split("\n")[0]));
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (sets.isEmpty()) {
                    // No drum set from songs in it: an SFZ kit's zip, onto the list.
                    addFile = file;
                    this.add();
                    return;
                }
                int pick = 0;
                // A combined set (the best of the songs' sets) is taken at once, named after the zip.
                if (zipPick == null && SongDrumSet.isCombined(sets.get(0))) {
                    final File dir = sets.get(0);
                    loading = new Thread(() -> fromFolder(app, dir, SongDrumSet.zipName(file.getName())), "pulsekit-zip-set");
                    loading.start();
                    return;
                }
                if (sets.size() > 1) {
                    String[] names = new String[sets.size()];
                    for (int i = 0; i < names.length; i++) names[i] = SongDrumSet.song(sets.get(i)) + (SongDrumSet.isCombined(sets.get(i)) ? " (combined)" : "");
                    if (zipPick != null) pick = zipPick;
                    else {
                        Object got = JOptionPane.showInputDialog(app, file.getName() + " has " + names.length + " drum sets: which one?", "Add zip",
                            JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
                        if (got == null) return;
                        pick = java.util.Arrays.asList(names).indexOf(got);
                    }
                }
                final File dir = sets.get(Math.max(0, Math.min(sets.size() - 1, pick)));
                loading = new Thread(() -> fromFolder(app, dir, null), "pulsekit-zip-set");
                loading.start();
            });
        }, "pulsekit-zip-read");
        loading.start();
    }

    /** A song's drum set folder (set.txt and its WAVs): a new drum set, selected and saved. */
    static void fromFolder(final Pulsekit app, final java.io.File dir, final String named) {
        final short[][] voices;
        try {
            voices = SongDrumSet.voices(dir, DrumPads.VOICE_RATE);
        } catch (Exception ex) {
            lastSongSet = "The song's drum set could not be read: " + ex.getMessage();
            javax.swing.SwingUtilities.invokeLater(() -> app.setNow(lastSongSet));
            return;
        }
        if (SongDrumSet.count(voices) == 0) {
            lastSongSet = "No drum in " + SongDrumSet.song(dir) + "'s set has a sound";
            javax.swing.SwingUtilities.invokeLater(() -> app.setNow(lastSongSet));
            return;
        }
        final String song = named != null ? named : SongDrumSet.song(dir);
        // Several hits of a drum (--set-hits): the song's files kept beside the sets (the run's folder may go).
        boolean several = false;
        try {
            several = SongDrumSet.hasLayers(SongDrumSet.layers(dir, DrumPads.VOICE_RATE));
        } catch (Exception ignored) {
            // the pads' sounds only
        }
        final boolean keepHits = several;
        javax.swing.SwingUtilities.invokeLater(() -> {
            Engine.DrumSet s = Engine.DrumSet.isolated(null, Engine.uniqueSetName(song + " drums", app.drumSets));
            DrumSetStore.fill(s, voices, "");
            app.drumSets.add(s);
            int layered = 0;
            if (keepHits) {
                java.io.File to = DrumSetStore.hitsDir(setsDir(), s);
                to.mkdirs();
                java.io.File[] files = dir.listFiles();
                if (files != null) for (java.io.File f : files) {
                    if (!f.isFile()) continue;
                    try {
                        java.nio.file.Files.copy(f.toPath(), new java.io.File(to, f.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ex) {
                        Logs.error("Song hits not kept", ex);
                    }
                }
                short[][][] l = songLayers(app, s.name);
                if (l != null) for (short[][] h : l) if (h != null && h.length > 1) layered++;
            }
            app.playback.drumSetIndex = app.drumSets.size() - 1;
            app.playback.reloadDrumBank();
            try {
                DrumSetStore.save(setsDir(), app.drumSets);
            } catch (Exception ex) {
                Logs.error("Drum sets not saved", ex);
            }
            app.playback.refreshDrumSets();
            StringBuilder pads = new StringBuilder();
            for (int t = 0; t < voices.length; t++) if (voices[t] != null) pads.append(pads.length() == 0 ? "" : ", ").append(Engine.TRACK_LABEL[t]);
            lastSongSet = "New drum set on the Pads page: " + s.name + " (" + pads + "; the other pads as Original)"
                + (layered > 0 ? "; " + layered + " drum" + (layered == 1 ? "" : "s") + " with several hits (vst_render's Pads set plays them as velocity layers)" : "");
            app.setNow(lastSongSet);
        });
    }

    /** A song set's several hits of each drum (kept in its <id>-hits folder), by the set's name; null without them. */
    static short[][][] songLayers(Pulsekit app, String name) {
        for (Engine.DrumSet s : new java.util.ArrayList<Engine.DrumSet>(app.drumSets)) {
            if (!s.name.equals(name)) continue;
            java.io.File dir = DrumSetStore.hitsDir(setsDir(), s);
            if (!new java.io.File(dir, SongDrumSet.INDEX).isFile()) return null;
            try {
                short[][][] l = SongDrumSet.layers(dir, DrumPads.VOICE_RATE);
                return SongDrumSet.hasLayers(l) ? l : null;
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    void saveSets() {
        try {
            DrumSetStore.save(setsDir(), app.drumSets);
        } catch (Exception ex) {
            Logs.error("Drum sets not saved", ex);
        }
    }
}
