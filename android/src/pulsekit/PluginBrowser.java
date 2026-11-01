package pulsekit;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PyJav > Plugins on the phone: the drum kits Android plays (SF2, SFZ) in their folders (Folders sets
 * them), with the same favourites, categories, groups, tags, filters, sorts and My order as the
 * desktop's Plugin Browser (plugin-library.txt). Types lists every type and which app uses it: VST,
 * AU, LV2 and CLAP plugins are desktop code, so the phone lists them as such. A kit's page: Use (onto
 * the pads), Test (a beat with its sounds), favourite, Category, Group, Tags, Up and Down.
 */
final class PluginBrowser {
    private PluginBrowser() {}

    static AlertDialog last;
    static AlertDialog page;
    static volatile String lastSaid = "";
    /** The tests' answers (Category, Group, Tags, Folders): null asks. */
    static java.util.function.Function<String, String> answer;

    static String type = PluginLibrary.ANY, sort = PluginLibrary.SORTS[0], category = PluginLibrary.ANY, group = PluginLibrary.ANY, tag = PluginLibrary.ANY, search = "";
    static boolean favourites;
    static final List<File> all = new ArrayList<File>();

    static String home() {
        return android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    static void show(final MainActivity app) {
        PluginLibrary.load(app.getFilesDir());
        PluginLibrary.androidClapDir = ClapHost.dir(app).getAbsolutePath();
        PluginLibrary.androidLv2Dir = Lv2Host.dir(app).getAbsolutePath();
        LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        if (last != null && last.isShowing()) last.dismiss();
        last = new AlertDialog.Builder(app).setTitle("Plugin Browser").setView(scroll).setPositiveButton("Close", null).create();
        rescan(app);
        fill(app, col);
        last.show();
    }

    /** What the CLAP folders' copying said last ("" when nothing new). */
    static String clapSynced = "";

    static void rescan() {
        rescan(null);
    }

    /** The lists found again; with the app, the CLAP folders' new plugins copied into Pulsekit's own folder first. */
    static void rescan(MainActivity app) {
        if (app != null) {
            clapSynced = ClapHost.sync(app, PluginLibrary.folders(PluginLibrary.CLAP, true, home()));
            if (clapSynced.length() > 0) say(app, clapSynced);
        }
        all.clear();
        all.addAll(PluginLibrary.scan(true, home(), null));
    }

    static void fill(final MainActivity app, final LinearLayout col) {
        col.removeAllViews();
        final Runnable again = () -> fill(app, col);
        LinearLayout types = app.row();
        for (final String t : new String[] {PluginLibrary.ANY, PluginLibrary.SF2, PluginLibrary.SFZ, PluginLibrary.CLAP, PluginLibrary.LV2}) {
            TextView chip = app.pill(t, t.equals(type), v -> {
                type = t;
                again.run();
            });
            chip.setTag("browser-type:" + t);
            types.addView(chip);
        }
        TextView fav = app.pill("\u2605 Favourites", favourites, v -> {
            favourites = !favourites;
            again.run();
        });
        fav.setTag("browser-favourites");
        types.addView(fav);
        col.addView(app.chipStrip(types));
        LinearLayout filters = app.row();
        filters.addView(chooser(app, "Sort: " + sort, "browser-sort", java.util.Arrays.asList(PluginLibrary.SORTS), s -> sort = s, again));
        filters.addView(chooser(app, "Category: " + category, "browser-category", withAny(PluginLibrary.categories()), s -> category = s, again));
        filters.addView(chooser(app, "Group: " + group, "browser-group", withAny(PluginLibrary.groups()), s -> group = s, again));
        filters.addView(chooser(app, "Tag: " + tag, "browser-tag", withAny(PluginLibrary.tags()), s -> tag = s, again));
        col.addView(app.chipStrip(filters));
        final EditText find = new EditText(app);
        find.setTag("browser-search");
        find.setHint("Search: part of a name, category, group or tag (* and ?)");
        find.setText(search);
        find.setSingleLine(true);
        find.setOnEditorActionListener((v, id, e) -> {
            search = find.getText().toString();
            again.run();
            return true;
        });
        col.addView(find);
        LinearLayout tools = app.row();
        TextView folders = app.outline("Folders", false, v -> folders(app, () -> {
            rescan(app);
            again.run();
        }));
        folders.setTag("browser-folders");
        tools.addView(folders);
        // Import .clap: a CLAP plugin built for Android, copied into Pulsekit's own folder.
        TextView importClap = app.outline("Import .clap", false, v -> pickFile(app, ClapHost.IMPORT_CLAP, "Pick a .clap built for Android (arm64)"));
        importClap.setTag("browser-import-clap");
        tools.addView(importClap);
        // Import LV2: the bundles of an aap-lv2 APK (or a zip of .lv2 folders) built for Android.
        TextView importLv2 = app.outline("Import LV2", false, v -> pickFile(app, Lv2Host.IMPORT_LV2, "Pick an aap-lv2 APK, or a zip of LV2 bundles built for Android"));
        importLv2.setTag("browser-import-lv2");
        tools.addView(importLv2);
        TextView typesInfo = app.outline("Types", false, v -> {
            lastSaid = PluginLibrary.typesTable(true);
            new AlertDialog.Builder(app).setTitle("Plugin and kit types").setMessage(lastSaid).setPositiveButton("Close", null).show();
        });
        typesInfo.setTag("browser-types");
        tools.addView(typesInfo);
        TextView rescan = app.outline("Rescan", false, v -> {
            rescan(app);
            again.run();
        });
        rescan.setTag("browser-rescan");
        tools.addView(rescan);
        col.addView(app.chipStrip(tools));
        final List<File> shown = PluginLibrary.shown(all, type, favourites, category, group, tag, search, sort);
        TextView count = app.text(shown.size() + " of " + all.size() + (all.isEmpty() ? " \u2014 no kits in the folders (Folders sets them; Pulsekit needs the files permission to look in Download)" : ""), 12, false);
        count.setTag("browser-count");
        count.setPadding(0, app.dp(6), 0, app.dp(4));
        col.addView(count);
        for (final File f : shown) {
            TextView line = app.text(PluginLibrary.line(f), 14, false);
            line.setTag("browser-item:" + PluginLibrary.name(f));
            line.setPadding(0, app.dp(8), 0, app.dp(8));
            line.setOnClickListener(v -> open(app, f, shown, again));
            col.addView(line);
        }
    }

    /** A kit's page: what Info says, and its menu. */
    static void open(final MainActivity app, final File f, final List<File> shown, final Runnable changed) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(18), app.dp(8), app.dp(18), app.dp(8));
        TextView info = app.text(PluginLibrary.info(f, true), 13, false);
        info.setTag("browser-info");
        info.setTextIsSelectable(true);
        col.addView(info);
        LinearLayout row = app.row();
        final Runnable reopen = () -> {
            changed.run();
            if (page != null) page.dismiss();
            open(app, f, PluginLibrary.shown(all, type, favourites, category, group, tag, search, sort), changed);
        };
        row.addView(tagged(app.outline(PluginLibrary.entry(f).favourite ? "Remove from favourites" : "Add to favourites", false, v -> {
            say(app, PluginLibrary.name(f) + (PluginLibrary.toggleFavourite(f) ? " is a favourite" : " is not a favourite any more"));
            reopen.run();
        }), "browser-fav"));
        for (final String what : new String[] {"Category", "Group", "Tags"}) {
            row.addView(tagged(app.outline(what, false, v -> ask(app, f, what, reopen)), "browser-set-" + what.toLowerCase(java.util.Locale.ROOT)));
        }
        row.addView(tagged(app.outline("Up", false, v -> {
            PluginLibrary.move(PluginLibrary.shown(all, type, favourites, category, group, tag, search, PluginLibrary.SORTS[6]), f, -1);
            sort = PluginLibrary.SORTS[6];
            reopen.run();
        }), "browser-up"));
        row.addView(tagged(app.outline("Down", false, v -> {
            PluginLibrary.move(PluginLibrary.shown(all, type, favourites, category, group, tag, search, PluginLibrary.SORTS[6]), f, 1);
            sort = PluginLibrary.SORTS[6];
            reopen.run();
        }), "browser-down"));
        col.addView(app.chipStrip(row));
        final boolean lv2 = PluginLibrary.typeOf(f) != null && PluginLibrary.typeOf(f).name.equals(PluginLibrary.LV2);
        final List<Lv2Bundle.Plugin> lv2Plugins = new ArrayList<Lv2Bundle.Plugin>();
        if (lv2) {
            // The bundle's plugins: one picked plays (Test, Render…).
            try {
                lv2Plugins.addAll(Lv2Bundle.read(f));
            } catch (Exception ex) {
                info.append("\n\nNot readable: " + ex.getMessage());
            }
            // Play, Pause, Stop on top: a bundle can list many plugins.
            col.addView(transport(app));
            col.addView(app.sectionLabel("PLUGINS IN IT (" + lv2Plugins.size() + ")"));
            final List<TextView> rows = new ArrayList<TextView>();
            final Runnable mark = () -> {
                Lv2Bundle.Plugin on = chosenLv2(f, lv2Plugins);
                for (int i = 0; i < rows.size(); i++) {
                    Lv2Bundle.Plugin p = lv2Plugins.get(i);
                    rows.get(i).setText((p == on ? "\u25C9 " : "\u25CB ") + p.name + "  \u00b7 " + p.role()
                        + (p.missingFeatures().isEmpty() ? "" : "  (needs " + String.join(", ", p.missingFeatures()) + ")"));
                }
            };
            for (final Lv2Bundle.Plugin p : lv2Plugins) {
                TextView r = app.text(p.name, 14, false);
                r.setTag("lv2-plugin:" + p.name);
                r.setPadding(0, app.dp(6), 0, app.dp(6));
                r.setOnClickListener(v -> {
                    LV2_CHOSEN.put(f.getAbsolutePath(), p.uri);
                    mark.run();
                });
                rows.add(r);
                col.addView(r);
            }
            mark.run();
        }
        if (PluginLibrary.typeOf(f) != null && PluginLibrary.typeOf(f).name.equals(PluginLibrary.CLAP)) col.addView(transport(app));
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        if (page != null && page.isShowing()) page.dismiss();
        boolean clap = PluginLibrary.typeOf(f) != null && PluginLibrary.typeOf(f).name.equals(PluginLibrary.CLAP);
        page = new AlertDialog.Builder(app).setTitle(PluginLibrary.name(f)).setView(scroll)
            .setPositiveButton(clap ? "Render MIDI\u2026" : lv2 ? "Render\u2026" : "Use", (d, w) -> use(app, f))
            .setNeutralButton("Test", null)
            .setNegativeButton("Close", null).create();
        page.show();
        final android.widget.Button test = page.getButton(AlertDialog.BUTTON_NEUTRAL);
        final java.util.function.Consumer<String> show = s -> app.runOnUiThread(() -> {
            info.append("\n\n" + s);
            say(app, s);
            test.setEnabled(true);
        });
        if (lv2) {
            test.setOnClickListener(v -> {
                Lv2Bundle.Plugin p = chosenLv2(f, lv2Plugins);
                if (p == null) return;
                test.setEnabled(false);
                testLv2(app, p, show);
            });
            return;
        }
        if (clap) {
            // Its plugins (ids, names), read by the host.
            ClapHost.list(app, f, b -> info.append("\n\n" + (b.getBoolean("ok") ? "Plugins in it:\n" + b.getString("list", "").trim().replace("\t", "  ") : "Not readable: " + b.getString("info"))));
            test.setOnClickListener(v -> {
                test.setEnabled(false);
                testClap(app, f, show);
            });
            return;
        }
        test.setOnClickListener(v -> test(app, f, show));
    }

    /** The last render (Test, Render…) played again, paused or stopped. */
    static android.view.View transport(final MainActivity app) {
        LinearLayout row = app.row();
        row.addView(tagged(app.outline("\u25B6 Play", false, v -> say(app, playLast())), "clap-play"));
        row.addView(tagged(app.outline("\u2759\u2759 Pause", false, v -> say(app, pauseLast())), "clap-pause"));
        row.addView(tagged(app.outline("\u25A0 Stop", false, v -> say(app, stopLast())), "clap-stop"));
        return app.chipStrip(row);
    }

    /** CLAP Test: the test tune played through it into a WAV in Download, and played. */
    static void testClap(final MainActivity app, final File f, final java.util.function.Consumer<String> said) {
        try {
            File midi = new File(app.getCacheDir(), "clap-test.mid");
            java.nio.file.Files.write(midi.toPath(), ClapHost.testMelody());
            renderClap(app, f, midi, "clap-test-" + PluginLibrary.name(f), said);
        } catch (Exception ex) {
            said.accept("Could not test " + PluginLibrary.name(f) + ": " + ex.getMessage());
        }
    }

    /** A MIDI file through the CLAP plugin: the WAV saved into Download and played; what was done to `said`. */
    static void renderClap(final MainActivity app, final File f, final File midi, final String name, final java.util.function.Consumer<String> said) {
        say(app, "Playing " + midi.getName() + " through " + PluginLibrary.name(f) + "\u2026");
        renderOut(app, PluginLibrary.name(f), name, (wav, done) -> ClapHost.render(app, f, "", midi, wav, 2.0, 1.0, done), said, midi);
    }

    /** The bundle's plugin picked on its page (the first instrument, else the first, until one is tapped). */
    static final java.util.Map<String, String> LV2_CHOSEN = new java.util.HashMap<String, String>();

    static Lv2Bundle.Plugin chosenLv2(File bundle, List<Lv2Bundle.Plugin> ps) {
        String uri = LV2_CHOSEN.get(bundle.getAbsolutePath());
        for (Lv2Bundle.Plugin p : ps) if (p.uri.equals(uri)) return p;
        for (Lv2Bundle.Plugin p : ps) if (p.takesMidi()) return p;
        return ps.isEmpty() ? null : ps.get(0);
    }

    /** LV2 Test: an instrument plays the test tune; an effect gets a test beat through it. */
    static void testLv2(final MainActivity app, final Lv2Bundle.Plugin p, final java.util.function.Consumer<String> said) {
        try {
            if (p.takesMidi()) {
                File midi = new File(app.getCacheDir(), "lv2-test.mid");
                java.nio.file.Files.write(midi.toPath(), ClapHost.testMelody());
                renderLv2(app, p, midi, null, "lv2-test-" + p.name, said);
            } else {
                File beat = new File(app.getCacheDir(), "lv2-test-beat.wav");
                java.nio.file.Files.write(beat.toPath(), AudioIo.encodeWav(Lv2Host.testBeat(), ClapHost.RATE));
                renderLv2(app, p, null, beat, "lv2-test-" + p.name, said);
            }
        } catch (Exception ex) {
            said.accept("Could not test " + p.name + ": " + ex.getMessage());
        }
    }

    static void renderLv2(final MainActivity app, final Lv2Bundle.Plugin p, final File midi, final File wavIn, final String name, final java.util.function.Consumer<String> said) {
        say(app, "Playing " + (midi != null ? midi.getName() : wavIn.getName()) + " through " + p.name + "\u2026");
        renderOut(app, p.name, name, (wav, done) -> Lv2Host.render(app, p, midi, wavIn, wav, midi != null ? 2.0 : 1.0, done), said, midi, wavIn);
    }

    /**
     * A render into Pulsekit's temp folder: the WAV saved into Download and played; what was done to
     * `said`. The `inputs` (the test tune, a picked file's copy) are deleted once it is done.
     */
    static void renderOut(final MainActivity app, final String plugin, final String name,
        final java.util.function.BiConsumer<File, java.util.function.Consumer<android.os.Bundle>> run, final java.util.function.Consumer<String> said,
        final File... inputs) {
        final File wav = new File(app.getCacheDir(), "plugin-render-" + System.nanoTime() + ".wav");
        run.accept(wav, b -> {
            for (File in : inputs) if (in != null) in.delete();
            if (!b.getBoolean("ok")) {
                wav.delete();
                said.accept(plugin + ": " + b.getString("info"));
                return;
            }
            final String info = b.getString("info").startsWith(plugin) ? b.getString("info") : plugin + " " + b.getString("info");
            new Thread(() -> {
                String where;
                try {
                    where = ArtJava.publish(app, name.replaceAll("[\\\\/:*?\"<>|]+", "_") + ".wav", java.nio.file.Files.readAllBytes(wav.toPath()));
                } catch (Exception ex) {
                    where = null;
                }
                final String saved = where;
                app.runOnUiThread(() -> {
                    play(wav);
                    said.accept(info + (saved == null ? "" : "; saved " + saved));
                });
            }, "pulsekit-clap-save").start();
        });
    }

    static android.media.MediaPlayer player;
    /** The WAV rendered last (Play plays it again); whether the player is paused. */
    static File lastWav;
    static boolean paused;

    /** A WAV played through Pulsekit's output (File > Sound devices), at its playback volume. */
    static void play(File wav) {
        // The render before: saved in Download already, its temp copy not needed.
        if (lastWav != null && !lastWav.equals(wav) && lastWav.getName().startsWith("plugin-render-")) lastWav.delete();
        lastWav = wav;
        paused = false;
        try {
            if (player != null) player.release();
            player = new android.media.MediaPlayer();
            player.setDataSource(wav.getAbsolutePath());
            SoundOut.prefer(player);
            player.prepare();
            player.start();
        } catch (Throwable ignored) {
            // saved; not played
        }
    }

    /** Play: a paused render goes on; else the last render from its start. */
    static String playLast() {
        if (player != null && paused) {
            player.start();
            paused = false;
            return "Playing on";
        }
        if (lastWav == null || !lastWav.isFile()) return "Nothing rendered yet: Test or Render MIDI\u2026 first";
        play(lastWav);
        return "Playing the last render";
    }

    static String pauseLast() {
        if (player == null || paused || !player.isPlaying()) return "Not playing";
        player.pause();
        paused = true;
        return "Paused: Play goes on";
    }

    static String stopLast() {
        if (player == null) return "Not playing";
        player.release();
        player = null;
        paused = false;
        return "Stopped";
    }

    /** The CLAP plugin Render MIDI… waits on a MIDI file for; the LV2 plugin Render… waits on a MIDI file or a WAV for. */
    static File pendingClap;
    static Lv2Bundle.Plugin pendingLv2;

    static void pickFile(MainActivity app, int code, String what) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            app.startActivityForResult(intent, code);
            app.setNow(what);
        } catch (Exception ex) {
            say(app, "No file picker on this phone: " + ex.getMessage());
        }
    }

    /** The pickers' files (MainActivity.onActivityResult): a .clap imported, or the MIDI file to render. */
    static void picked(final MainActivity app, int code, android.net.Uri uri) {
        String name = DbImport.displayName(app, uri);
        if (code == ClapHost.IMPORT_CLAP) {
            try {
                File f = ClapHost.importClap(app, uri, name);
                rescan();
                say(app, "Imported " + f.getName() + ": it is in the Plugin Browser (CLAP)");
                if (last != null && last.isShowing()) show(app);
            } catch (Exception ex) {
                say(app, "Not imported: " + ex.getMessage());
                android.widget.Toast.makeText(app, lastSaid, android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (code == Lv2Host.IMPORT_LV2) {
            try {
                String said = Lv2Host.importBundles(app, uri, name);
                rescan();
                say(app, said);
                android.widget.Toast.makeText(app, said, android.widget.Toast.LENGTH_LONG).show();
                if (last != null && last.isShowing()) show(app);
            } catch (Exception ex) {
                say(app, "Not imported: " + ex.getMessage());
                android.widget.Toast.makeText(app, lastSaid, android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        final Lv2Bundle.Plugin lv2 = pendingLv2;
        pendingLv2 = null;
        if (lv2 != null) {
            try {
                boolean midi = code == ClapHost.RENDER_MIDI;
                File in = DbSettings.copyIn(app, uri, midi ? "lv2-input.mid" : "lv2-input.wav");
                String base = name == null ? "input" : name.replaceAll("(?i)\\.(midi?|wave?)$", "");
                renderLv2(app, lv2, midi ? in : null, midi ? null : in, base + "-" + lv2.name, s -> {
                    say(app, s);
                    android.widget.Toast.makeText(app, s, android.widget.Toast.LENGTH_LONG).show();
                });
            } catch (Exception ex) {
                say(app, "Could not read " + name + ": " + ex.getMessage());
            }
            return;
        }
        final File clap = pendingClap;
        pendingClap = null;
        if (clap == null) return;
        try {
            File midi = DbSettings.copyIn(app, uri, "clap-input.mid");
            String base = name == null ? "song" : name.replaceAll("(?i)\\.midi?$", "");
            renderClap(app, clap, midi, base + "-" + PluginLibrary.name(clap), s -> {
                say(app, s);
                android.widget.Toast.makeText(app, s, android.widget.Toast.LENGTH_LONG).show();
            });
        } catch (Exception ex) {
            say(app, "Could not read " + name + ": " + ex.getMessage());
        }
    }

    /** Use: the kit onto the pads (as File > Import does). */
    static void use(MainActivity app, File f) {
        PluginLibrary.Type t = PluginLibrary.typeOf(f);
        if (t != null && t.name.equals(PluginLibrary.SF2)) {
            try {
                app.projectIo.ingest(java.nio.file.Files.readAllBytes(f.toPath()), f.getName(), null);
                say(app, PluginLibrary.name(f) + " is loading onto the pads (SoundFont)");
            } catch (Exception ex) {
                say(app, "Could not load " + f.getName() + ": " + ex.getMessage());
            }
        } else if (t != null && t.name.equals(PluginLibrary.SFZ)) {
            app.projectIo.loadSfz(f, f.getName());
            say(app, "Loading " + PluginLibrary.name(f) + " onto the pads\u2026");
        } else if (t != null && t.name.equals(PluginLibrary.LV2)) {
            // Render…: the plugin picked on the page; an instrument takes a MIDI file, an effect a WAV.
            List<Lv2Bundle.Plugin> ps;
            try {
                ps = Lv2Bundle.read(f);
            } catch (Exception ex) {
                say(app, PluginLibrary.name(f) + ": " + ex.getMessage());
                return;
            }
            pendingLv2 = chosenLv2(f, ps);
            if (pendingLv2 == null) return;
            boolean midi = pendingLv2.takesMidi();
            pickFile(app, midi ? ClapHost.RENDER_MIDI : Lv2Host.PROCESS_WAV, (midi ? "Pick the MIDI file to play through " : "Pick the WAV to play through ") + pendingLv2.name);
        } else if (t != null && t.name.equals(PluginLibrary.CLAP)) {
            // Render MIDI…: a MIDI file picked, played through it into a WAV.
            pendingClap = f;
            pickFile(app, ClapHost.RENDER_MIDI, "Pick the MIDI file to play through " + PluginLibrary.name(f));
        } else {
            say(app, PluginLibrary.name(f) + ": " + (t == null ? "not a kit" : t.how));
        }
    }

    /** Test: two bars of a beat with the kit's sounds, through Pulsekit's output (File > Sound devices). */
    static void test(final MainActivity app, final File f, final java.util.function.Consumer<String> said) {
        new Thread(() -> {
            try {
                short[] beat = PluginLibrary.testBeat(PluginLibrary.kitVoices(f));
                String s = "Test beat with " + PluginLibrary.name(f) + ": " + (beat.length / AudioIo.SR) + " s";
                String played = "";
                try {
                    android.media.AudioTrack t = new android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC, AudioIo.SR, android.media.AudioFormat.CHANNEL_OUT_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT, Math.max(2, beat.length * 2), android.media.AudioTrack.MODE_STATIC);
                    t.write(beat, 0, beat.length);
                    SoundOut.prefer(t);
                    t.play();
                    Thread.sleep(beat.length * 1000L / AudioIo.SR + 200);
                    t.release();
                } catch (Throwable notPlayed) {
                    played = " (not played: " + notPlayed.getMessage() + ")";
                }
                // Said once, after it played (or could not).
                said.accept(s + played);
            } catch (Throwable ex) {
                said.accept("Could not test " + PluginLibrary.name(f) + ": " + ex.getMessage());
            }
        }, "kit-test").start();
    }

    /** Category, Group or Tags: typed, or one in use tapped (empty clears). */
    static void ask(final MainActivity app, final File f, final String what, final Runnable done) {
        if (answer != null) {
            set(f, what, answer.apply(what));
            say(app, PluginLibrary.name(f) + ": " + what.toLowerCase(java.util.Locale.ROOT) + " set");
            done.run();
            return;
        }
        PluginLibrary.Entry e = PluginLibrary.entry(f);
        final EditText field = new EditText(app);
        field.setText(what.equals("Category") ? e.category : what.equals("Group") ? e.group : String.join(", ", e.tags));
        field.setHint(what.equals("Tags") ? "comma between tags" : what);
        LinearLayout col = app.col();
        col.setPadding(app.dp(18), app.dp(8), app.dp(18), 0);
        col.addView(field);
        List<String> known = what.equals("Category") ? PluginLibrary.categories() : what.equals("Group") ? PluginLibrary.groups() : PluginLibrary.tags();
        LinearLayout chips = app.row();
        for (final String k : known) {
            chips.addView(app.pill(k, false, v -> {
                String now = field.getText().toString().trim();
                field.setText(what.equals("Tags") && now.length() > 0 ? now + ", " + k : k);
            }));
        }
        if (!known.isEmpty()) col.addView(app.chipStrip(chips));
        new AlertDialog.Builder(app).setTitle(what + " of " + PluginLibrary.name(f)).setView(col)
            .setPositiveButton("OK", (d, w) -> {
                set(f, what, field.getText().toString());
                done.run();
            }).setNegativeButton("Cancel", null).show();
    }

    static void set(File f, String what, String value) {
        if (value == null) return;
        if (what.equals("Category")) PluginLibrary.setCategory(f, value);
        else if (what.equals("Group")) PluginLibrary.setGroup(f, value);
        else PluginLibrary.setTags(f, value);
    }

    /** Folders: SF2's and SFZ's folders (one a line), Defaults, Save. */
    static void folders(final MainActivity app, final Runnable saved) {
        if (answer != null) {
            String got = answer.apply("Folders");
            if (got != null) {
                List<String> paths = lines(got);
                PluginLibrary.setFolders(PluginLibrary.SF2, paths);
                PluginLibrary.setFolders(PluginLibrary.SFZ, paths);
                saved.run();
            }
            return;
        }
        LinearLayout col = app.col();
        col.setPadding(app.dp(18), app.dp(8), app.dp(18), 0);
        // CLAP: folders its plugins are copied from (Android runs a plugin only from Pulsekit's own folder).
        final String[] kinds = {PluginLibrary.SF2, PluginLibrary.SFZ, PluginLibrary.CLAP};
        final List<List<String>> lists = new ArrayList<List<String>>();
        for (int i = 0; i < kinds.length; i++) {
            final List<String> paths = new ArrayList<String>(PluginLibrary.folders(kinds[i], true, home()));
            lists.add(paths);
            col.addView(app.sectionLabel(kinds[i] + " FOLDERS (" + paths.size() + ")"));
            // Its folders in a list; Add folder… browses for one, Change… edits or browses the one shown, Delete removes it.
            final android.widget.Spinner shown = new android.widget.Spinner(app);
            shown.setTag("kit-folders:" + kinds[i]);
            final TextView head = (TextView) col.getChildAt(col.getChildCount() - 1);
            final String kind = kinds[i];
            // A CLAP folder is kept as the picker's folder (Android lets Pulsekit read it), shown as its path.
            final boolean asTree = kind.equals(PluginLibrary.CLAP);
            final Runnable refresh = () -> {
                List<String> items = new ArrayList<String>();
                for (String p : paths) items.add(shown(p));
                if (items.isEmpty()) items.add(FolderPaths.NONE);
                int at = Math.max(0, Math.min(shown.getSelectedItemPosition(), items.size() - 1));
                shown.setAdapter(new android.widget.ArrayAdapter<String>(app, android.R.layout.simple_spinner_dropdown_item, items));
                shown.setSelection(at);
                head.setText(kind + " FOLDERS (" + paths.size() + ")");
            };
            refresh.run();
            col.addView(shown);
            LinearLayout row = app.row();
            TextView add = app.outline(FolderPaths.ADD, false, v -> pickFolder(app, p -> {
                if (!paths.contains(p)) paths.add(p);
                refresh.run();
                shown.setSelection(paths.indexOf(p));
            }, asTree));
            add.setTag("kit-folder-add:" + kind);
            TextView change = app.outline(FolderPaths.CHANGE, false, v -> {
                if (paths.isEmpty()) return;
                final int at = shown.getSelectedItemPosition();
                changeFolder(app, paths.get(at), asTree, p -> {
                    if (p.length() == 0) paths.remove(at);
                    else paths.set(at, p);
                    refresh.run();
                });
            });
            change.setTag("kit-folder-change:" + kind);
            TextView delete = app.outline(FolderPaths.DELETE, false, v -> {
                if (paths.isEmpty()) return;
                paths.remove(shown.getSelectedItemPosition());
                refresh.run();
            });
            delete.setTag("kit-folder-delete:" + kind);
            for (TextView b : new TextView[] {add, change, delete}) row.addView(b);
            col.addView(app.chipStrip(row));
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(app);
        scroll.addView(col);
        lastFolders = new AlertDialog.Builder(app).setTitle("Kit folders").setView(scroll)
            .setPositiveButton("Save", (d, w) -> {
                for (int i = 0; i < kinds.length; i++) {
                    List<String> paths = lists.get(i);
                    PluginLibrary.setFolders(kinds[i], paths.equals(PluginLibrary.defaults(kinds[i], true, home())) ? null : paths);
                }
                saved.run();
            })
            .setNeutralButton("Defaults", (d, w) -> {
                for (String k : kinds) PluginLibrary.setFolders(k, null);
                saved.run();
            })
            .setNegativeButton("Cancel", null).show();
    }

    static final int PICK_KIT_FOLDER = 94;
    /** For the tests: the Kit folders window and the Change window shown last. */
    static AlertDialog lastFolders;
    static AlertDialog lastChange;
    /** Where the folder picked goes (Add folder…, Change…'s Browse…), and whether as the picker's folder (content://) or its path. */
    static java.util.function.Consumer<String> pendingFolder;
    static boolean pendingAsTree;

    /** A folder as the list shows it: its path (a picked folder's too). */
    static String shown(String p) {
        if (!p.startsWith("content://")) return p;
        try {
            String path = FolderPaths.fromDocId(android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(p)));
            return path == null ? p : path;
        } catch (Exception ex) {
            return p;
        }
    }

    /** Android's folder picker; the folder picked (as its path, or `asTree` the picker's folder) goes to `got`. */
    static void pickFolder(MainActivity app, java.util.function.Consumer<String> got, boolean asTree) {
        pendingFolder = got;
        pendingAsTree = asTree;
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            app.startActivityForResult(intent, PICK_KIT_FOLDER);
        } catch (Exception ex) {
            say(app, "No folder picker on this phone: " + ex.getMessage());
        }
    }

    /** The picker's folder (MainActivity.onActivityResult): its path to where it was asked for. */
    static void folderPicked(MainActivity app, android.net.Uri tree) {
        java.util.function.Consumer<String> got = pendingFolder;
        pendingFolder = null;
        if (tree == null || got == null) return;
        try {
            app.getContentResolver().takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // read through its path; the permission only helps
        }
        String path = FolderPaths.fromDocId(android.provider.DocumentsContract.getTreeDocumentId(tree));
        if (pendingAsTree) {
            got.accept(tree.toString());
            say(app, "Folder " + (path == null ? tree.toString() : path));
            return;
        }
        if (path == null) {
            say(app, "That folder is not on the phone's storage: pick one under Internal storage or the memory card");
            return;
        }
        got.accept(path);
        say(app, "Folder " + path);
    }

    /** Change…: the path to edit, or Browse… for another folder in its place; OK keeps it (empty: removed). */
    static void changeFolder(final MainActivity app, String path, final boolean asTree, final java.util.function.Consumer<String> done) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(18), app.dp(8), app.dp(18), 0);
        final EditText field = new EditText(app);
        // A picked folder is kept as it is unless its path is typed over.
        final String[] picked = {path};
        field.setText(shown(path));
        field.setTag("kit-folder-path");
        col.addView(field);
        TextView browse = app.outline(FolderPaths.BROWSE, false, v -> pickFolder(app, p -> {
            picked[0] = p;
            field.setText(shown(p));
        }, asTree));
        browse.setTag("kit-folder-browse");
        col.addView(browse);
        lastChange = new AlertDialog.Builder(app).setTitle("Change folder").setView(col)
            .setPositiveButton("OK", (d, w) -> {
                String typed = field.getText().toString().trim();
                done.accept(typed.equals(shown(picked[0])) ? picked[0] : typed);
            })
            .setNegativeButton("Cancel", null).show();
    }

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<String>();
        for (String l : text.split("\n")) if (l.trim().length() > 0) out.add(l.trim());
        return out;
    }

    private static TextView chooser(final MainActivity app, String label, String tag, final List<String> values, final java.util.function.Consumer<String> picked, final Runnable again) {
        TextView chip = app.pill(label, false, v -> new AlertDialog.Builder(app).setItems(values.toArray(new String[0]), (d, w) -> {
            picked.accept(values.get(w));
            again.run();
        }).show());
        chip.setTag(tag);
        return chip;
    }

    private static List<String> withAny(List<String> values) {
        List<String> out = new ArrayList<String>();
        out.add(PluginLibrary.ANY);
        out.addAll(values);
        return out;
    }

    private static TextView tagged(TextView v, String tag) {
        v.setTag(tag);
        return v;
    }

    static void say(MainActivity app, String s) {
        lastSaid = s;
        app.setNow(s);
    }
}
