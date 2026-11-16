package pulsekit;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The Pads page's drum sets and kits: the sets in a row (+ makes one; a long press: Rename, Copy into new,
 * Match Original, Match empty from Original, Delete), and under them Add SF2 (a SoundFont or an SFZ kit from
 * the phone or Pulsekit's DB) with the kits added in a drop-down list: one picked loads into the set selected.
 * The sets are kept (DrumSetStore), the kits too (KitShelf).
 */
final class PadKits {
    final MainActivity app;
    Spinner kitList;
    /** The list's entries as shown (the first: none picked). */
    final List<KitShelf.Kit> listed = new ArrayList<KitShelf.Kit>();
    /** True while the list is set from code: its listener does nothing then. */
    boolean quiet;
    /** The system picker's request (Add SF2 > Other…). */
    static final int PICK = 4731;
    /** The last menu or question shown (for the tests). */
    static AlertDialog lastDialog;
    /** Tests: the answer a name question gets (null: asked). */
    static String answer;

    PadKits(MainActivity app) {
        this.app = app;
    }

    static File setsDir(MainActivity app) {
        return new File(app.getFilesDir(), "drumsets");
    }

    static File kitsDir(MainActivity app) {
        return new File(app.getFilesDir(), "kits");
    }

    /** The row under the drum sets: Add SF2 and the kits' list. */
    LinearLayout kitRow() {
        LinearLayout row = app.row();
        TextView add = app.outline("Add SF2", false, v -> this.add());
        add.setTag("pads-add-kit");
        row.addView(add);
        this.kitList = new Spinner(app);
        this.kitList.setTag("pads-kit-list");
        this.kitList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (PadKits.this.quiet || pos <= 0 || pos > PadKits.this.listed.size()) return;
                PadKits.this.load(PadKits.this.listed.get(pos - 1));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        this.kitList.setOnLongClickListener(v -> {
            this.kitMenu();
            return true;
        });
        row.addView(this.kitList, new LinearLayout.LayoutParams(0, -2, 1f));
        this.refreshKits();
        return row;
    }

    /** The list again: the kits on the shelf, the selected set's kit selected. */
    void refreshKits() {
        if (this.kitList == null) return;
        this.listed.clear();
        this.listed.addAll(KitShelf.list(kitsDir(app)));
        List<String> names = new ArrayList<String>();
        names.add(this.listed.isEmpty() ? "No kits yet: Add SF2" : "Kit…");
        int at = 0;
        String cur = app.playback.activeDrumSet().kit;
        for (int i = 0; i < this.listed.size(); i++) {
            names.add(this.listed.get(i).name);
            if (this.listed.get(i).name.equals(cur)) at = i + 1;
        }
        ArrayAdapter<String> a = new ArrayAdapter<String>(app, android.R.layout.simple_spinner_item, names);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.quiet = true;
        this.kitList.setAdapter(a);
        this.kitList.setSelection(at, false);
        this.kitList.post(() -> this.quiet = false);
    }

    /** Add SF2: Pulsekit's file browser (its DB, Download, the folders), Other… the phone's picker. */
    void add() {
        Runnable system = () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            app.startActivityForResult(intent, PICK);
        };
        MediaBrowser.chooseFor(app, "pads-kit", (name, file) -> this.added(file, name), system);
    }

    /** The phone's picker's file: copied into the cache, then added. */
    void picked(final Uri uri) {
        final String name = DbImport.displayName(app, uri);
        app.setNow("Reading " + name + "…");
        new Thread(() -> {
            File dir = new File(app.getCacheDir(), "kit-add");
            dir.mkdirs();
            File copy = new File(dir, name.replaceAll("[\\\\/:]", "_"));
            try (java.io.InputStream in = app.getContentResolver().openInputStream(uri); java.io.OutputStream out = new java.io.FileOutputStream(copy)) {
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            } catch (Exception ex) {
                app.handler.post(() -> Toast.makeText(app, "Could not read " + name + ": " + ex.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            app.handler.post(() -> this.added(copy, name));
        }, "pulsekit-kit-add").start();
    }

    /** A file picked: onto the shelf (in the background), then into the selected set. */
    void added(final File file, final String name) {
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".sfz")) {
            Toast.makeText(app, "An SFZ kit needs its samples: add the kit's .zip or .tar.gz (the .sfz with its sample folders)", Toast.LENGTH_LONG).show();
            return;
        }
        if (!KitShelf.takes(name)) {
            Toast.makeText(app, name + " is not a SoundFont (.sf2) or an SFZ kit package", Toast.LENGTH_LONG).show();
            return;
        }
        app.setNow("Adding " + name + "…");
        new Thread(() -> {
            try {
                final List<KitShelf.Kit> kits = KitShelf.add(kitsDir(app), file, name);
                app.handler.post(() -> {
                    this.refreshKits();
                    Toast.makeText(app, kits.size() == 1 ? "Added " + kits.get(0).name : "Added " + kits.size() + " kits from " + name, Toast.LENGTH_SHORT).show();
                    this.load(kits.get(0));
                });
            } catch (Exception ex) {
                app.handler.post(() -> Toast.makeText(app, "Could not add " + name + ": " + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "pulsekit-kit-add").start();
    }

    /** A kit into the selected set (Original: the pads' own kit, kept as kit.sf2). */
    void load(final KitShelf.Kit kit) {
        final Engine.DrumSet set = app.playback.activeDrumSet();
        app.setNow("Loading " + kit.name + "…");
        new Thread(() -> {
            try {
                final short[][] v = KitShelf.voices(kitsDir(app), kit);
                final byte[] sf2 = set.original ? AudioIo.encodeSf2(v, kit.name) : null;
                app.handler.post(() -> {
                    if (set.original) {
                        app.playback.applySf2(v);
                        app.persistence.persistSf2(sf2);
                        set.kit = kit.name;
                    } else {
                        DrumSetStore.fill(set, v, kit.name);
                        int n = 0;
                        for (short[] x : v) if (x != null) n++;
                        Toast.makeText(app, set.name + " · " + kit.name + " · " + n + " drums", Toast.LENGTH_SHORT).show();
                    }
                    this.saveSets();
                    app.playback.refreshDrumSets();
                });
            } catch (Exception ex) {
                app.handler.post(() -> {
                    Toast.makeText(app, "Could not load " + kit.name + ": " + String.valueOf(ex.getMessage()).split("\n")[0], Toast.LENGTH_LONG).show();
                    this.refreshKits();
                });
            }
        }, "pulsekit-kit-load").start();
    }

    /** A long press on the list: take a kit off it. */
    void kitMenu() {
        if (this.listed.isEmpty()) return;
        String[] items = new String[this.listed.size()];
        for (int i = 0; i < items.length; i++) items[i] = this.listed.get(i).name;
        lastDialog = new AlertDialog.Builder(app).setTitle("Remove a kit from the list").setItems(items, (d, w) -> {
            final KitShelf.Kit k = this.listed.get(w);
            lastDialog = new AlertDialog.Builder(app).setMessage("Remove " + k.name + " from the list? The drum sets keep their sounds.")
                .setPositiveButton("Remove", (d2, w2) -> {
                    try {
                        KitShelf.remove(kitsDir(app), k);
                    } catch (Exception ignored) {
                        // listed again as it was
                    }
                    this.refreshKits();
                }).setNegativeButton("Cancel", null).show();
        }).setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------- drum sets

    /** +: a new set (its pads matching Original until a kit is loaded into it), selected. */
    void newSet() {
        this.askName("New drum set", Engine.uniqueSetName("Drum set", app.drumSets), name -> {
            Engine.DrumSet s = Engine.DrumSet.isolated(null, Engine.uniqueSetName(name, app.drumSets));
            for (int t = 0; t < Engine.TRACK_ID.length; t++) s.matchOrig[t] = true;
            app.drumSets.add(s);
            this.select(app.drumSets.size() - 1);
            app.setNow(s.name + ": pick a kit for it");
        });
    }

    void select(int i) {
        app.playback.drumSetIndex = i;
        this.saveSets();
        app.playback.refreshDrumSets();
    }

    /** A set's long press: Rename, Copy into new, Match Original, Match empty from Original, Delete. */
    void setMenu(final int i) {
        final Engine.DrumSet s = app.drumSets.get(i);
        final List<String> items = new ArrayList<String>();
        if (!s.original) items.add("Rename");
        items.add("Copy into new");
        if (!s.original) {
            items.add("Match Original");
            items.add("Match empty from Original");
            items.add("Delete");
        }
        lastDialog = new AlertDialog.Builder(app).setTitle(s.name).setItems(items.toArray(new String[0]), (d, w) -> {
            String what = items.get(w);
            if (what.equals("Rename")) {
                this.askName("Rename drum set", s.name, name -> {
                    List<Engine.DrumSet> others = new ArrayList<Engine.DrumSet>(app.drumSets);
                    others.remove(s);
                    s.name = Engine.uniqueSetName(name, others);
                    this.select(i);
                });
            } else if (what.equals("Copy into new")) {
                this.askName("Copy into a new drum set", Engine.uniqueSetName(s.name + " copy", app.drumSets), name -> {
                    Engine.DrumSet c = s.copyAs(Engine.uniqueSetName(name, app.drumSets));
                    if (s.original) {
                        // Original's sounds are the pads' own: the copy keeps them as its own.
                        for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                            c.samples[t] = app.voices[Engine.soundTrack(t)];
                            c.matchOrig[t] = c.samples[t] == null;
                        }
                    }
                    app.drumSets.add(c);
                    this.select(app.drumSets.size() - 1);
                });
            } else if (what.equals("Match Original")) {
                for (int t = 0; t < Engine.TRACK_ID.length; t++) s.matchOrig[t] = true;
                this.select(i);
                app.setNow(s.name + " matches Original");
            } else if (what.equals("Match empty from Original")) {
                for (int t = 0; t < Engine.TRACK_ID.length; t++) if (s.samples[t] == null) s.matchOrig[t] = true;
                this.select(i);
                app.setNow(s.name + ": empty pads match Original");
            } else if (what.equals("Delete")) {
                lastDialog = new AlertDialog.Builder(app).setMessage("Delete the drum set " + s.name + "?")
                    .setPositiveButton("Delete", (d2, w2) -> {
                        app.drumSets.remove(s);
                        if (app.playback.drumSetIndex >= app.drumSets.size() || app.playback.drumSetIndex == i) app.playback.drumSetIndex = 0;
                        else if (app.playback.drumSetIndex > i) app.playback.drumSetIndex--;
                        this.select(app.playback.drumSetIndex);
                        app.setNow("Drum set " + s.name + " deleted");
                    }).setNegativeButton("Cancel", null).show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    interface Named {
        void named(String name);
    }

    void askName(String title, String suggested, final Named done) {
        if (answer != null) {
            String a = answer;
            answer = null;
            if (a.trim().length() > 0) done.named(a.trim());
            return;
        }
        final EditText field = new EditText(app);
        field.setSingleLine(true);
        field.setText(suggested);
        field.setSelectAllOnFocus(true);
        field.setTag("pads-set-name");
        lastDialog = new AlertDialog.Builder(app).setTitle(title).setView(field).setPositiveButton("OK", (d, w) -> {
            String n = field.getText().toString().trim();
            if (n.length() > 0) done.named(n);
        }).setNegativeButton("Cancel", null).show();
    }

    void saveSets() {
        try {
            DrumSetStore.save(setsDir(app), app.drumSets);
        } catch (Exception ex) {
            Logs.error("Drum sets not saved", ex);
        }
    }
}
