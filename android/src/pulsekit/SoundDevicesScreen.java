package pulsekit;

import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * File > Sound devices: the phone's outputs and inputs (USB through OTG, Bluetooth, wired, the
 * speaker), MIDI devices, drivers and USB devices (SoundOut). A tap opens a device: its details,
 * Use for Pulsekit (an output: Pulsekit's players prefer it; a MIDI output: the drum tracks play
 * through it) and Test. Use the default, System sound settings, Copy report and Refresh are on top.
 */
final class SoundDevicesScreen {
    private SoundDevicesScreen() {}

    static AlertDialog last;
    static AlertDialog device;
    /** What the last Test said (the tests read it). */
    static volatile String lastTest = "";

    static void show(final MainActivity app) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        if (last != null && last.isShowing()) last.dismiss();
        last = new AlertDialog.Builder(app).setTitle("Sound devices").setView(scroll).setPositiveButton("Close", null).create();
        List<SoundDevices.Device> found = fill(app, col);
        SoundDevices.log("Sound devices found (" + found.size() + ")", SoundDevices.report(header(app), found));
        last.show();
    }

    static List<SoundDevices.Device> fill(final MainActivity app, final LinearLayout col) {
        col.removeAllViews();
        AppSettings.load(app.getFilesDir());
        final List<SoundDevices.Device> found = SoundOut.list(app);
        TextView now = app.text("Pulsekit's sound output: " + SoundDevices.outNow() + "\nDrum MIDI output: " + SoundDevices.midiNow()
            + (SoundDevices.missing(found).isEmpty() ? "" : "\n" + SoundDevices.missing(found).trim()), 13, false);
        now.setTag("sound-now");
        col.addView(now);
        // Pulsekit's own levels: its players' volume, the gain on what it records.
        col.addView(level(app, InputLevel.PLAYBACK, "sound-play-volume", 100, AppSettings.playVolume(), p -> {
            AppSettings.setPlayVolume(p);
            SoundOut.volumeChanged();
        }));
        col.addView(level(app, InputLevel.RECORDING, "sound-record-level", 400, AppSettings.recordLevel(), AppSettings::setRecordLevel));
        LinearLayout buttons = app.row();
        TextView reset = app.outline("Use the default", false, v -> {
            AppSettings.setSoundOut("");
            AppSettings.setMidiOut("");
            SoundOut.closeDrumMidi();
            SoundOut.prefer(app.track);
            SoundDevices.log("Back to the defaults", SoundDevices.DEFAULT_OUT + ", " + SoundDevices.OWN_SYNTH);
            app.setNow("Pulsekit plays through the phone's own routing and its own drum sounds");
            fill(app, col);
        });
        reset.setTag("sound-default");
        buttons.addView(reset);
        // Only the drums back to Pulsekit's own sounds (the audio output kept).
        TextView ownDrums = app.outline("Own drum sounds", false, v -> {
            useOwnDrums(app);
            fill(app, col);
        });
        ownDrums.setTag("sound-midi-default");
        buttons.addView(ownDrums);
        TextView system = app.outline("System sound settings", false, v -> {
            try {
                app.startActivity(new android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS));
                SoundDevices.log("System sound settings", "Opened");
            } catch (Exception ex) {
                app.setNow("No sound settings screen: " + ex.getMessage());
            }
        });
        system.setTag("sound-system");
        buttons.addView(system);
        TextView copy = app.outline("Copy report", false, v -> {
            String report = SoundDevices.report(header(app), found);
            android.content.ClipboardManager clips = (android.content.ClipboardManager) app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            clips.setPrimaryClip(android.content.ClipData.newPlainText("Pulsekit sound devices", report));
            app.setNow("Copied the sound devices report (" + found.size() + " devices)");
        });
        copy.setTag("sound-copy");
        buttons.addView(copy);
        TextView refresh = app.outline("Refresh", false, v -> fill(app, col));
        refresh.setTag("sound-refresh");
        buttons.addView(refresh);
        col.addView(app.chipStrip(buttons));
        for (String kind : SoundDevices.KINDS) {
            List<SoundDevices.Device> some = SoundDevices.of(found, kind);
            TextView head = app.sectionLabel(kind.toUpperCase(java.util.Locale.ROOT) + " (" + some.size() + ")");
            head.setTag("sound-kind:" + kind);
            col.addView(head);
            for (final SoundDevices.Device d : some) {
                TextView line = app.text(d.label(), 14, false);
                line.setTag("sound-device:" + d.kind + ":" + d.key());
                line.setPadding(app.dp(8), app.dp(8), 0, app.dp(8));
                line.setOnClickListener(v -> open(app, d, () -> fill(app, col)));
                col.addView(line);
            }
        }
        return found;
    }

    /** One device: what is known of it, Use for Pulsekit (an output) and Test. */
    static void open(final MainActivity app, final SoundDevices.Device d, final Runnable changed) {
        TextView body = app.text(d.kind + (d.driver.isEmpty() ? "" : ", " + d.driver) + (SoundDevices.used(d) ? "\nUsed by Pulsekit" : "") + "\n\n" + d.details, 13, false);
        body.setTag("sound-info");
        body.setTextIsSelectable(true);
        body.setPadding(app.dp(18), app.dp(8), app.dp(18), app.dp(8));
        LinearLayout content = app.col();
        content.addView(body);
        ScrollView scroll = new ScrollView(app);
        scroll.addView(content);
        if (device != null && device.isShowing()) device.dismiss();
        AlertDialog.Builder b = new AlertDialog.Builder(app).setTitle(d.name).setView(scroll).setNegativeButton("Close", null);
        // A MIDI output with several input ports (an AAP plugin app: one an instrument): which one the drums go to.
        if (SoundDevices.MIDI_OUT.equals(d.kind)) {
            final java.util.List<String[]> ports = SoundOut.inputPorts(app, d);
            if (ports.size() > 1) {
                LinearLayout box = app.col();
                box.setPadding(app.dp(18), 0, app.dp(18), app.dp(8));
                box.addView(app.sectionLabel("DRUMS GO TO PORT"));
                LinearLayout chips = app.row();
                for (final String[] port : ports) {
                    final int number = Integer.parseInt(port[0]);
                    boolean on = number == AppSettings.midiOutPort();
                    TextView chip = app.pill(port[0] + (port[1].isEmpty() ? "" : ": " + port[1]), on, v -> {
                        AppSettings.setMidiOutPort(number);
                        if (SoundDevices.used(d)) SoundOut.openDrumMidi(app);
                        app.setNow("The drums go to " + d.name + "'s port " + port[0] + (port[1].isEmpty() ? "" : " (" + port[1] + ")"));
                        if (device != null) device.dismiss();
                        open(app, d, changed);
                    });
                    chip.setTag("sound-port:" + port[0]);
                    chips.addView(chip);
                }
                box.addView(app.chipStrip(chips));
                content.addView(box);
            }
        }
        if (SoundDevices.used(d)) {
            // In use: Stop using puts the default back (the phone's routing, or Pulsekit's own drum sounds).
            b.setPositiveButton("Stop using", (x, w) -> {
                if (SoundDevices.MIDI_OUT.equals(d.kind)) useOwnDrums(app);
                else {
                    AppSettings.setSoundOut("");
                    SoundOut.prefer(app.track);
                    SoundDevices.log("Back to the default output", SoundDevices.DEFAULT_OUT);
                    app.setNow("Pulsekit plays through the phone's own routing");
                }
                changed.run();
            });
        } else if (SoundDevices.usable(d)) {
            b.setPositiveButton("Use for Pulsekit", (x, w) -> {
                boolean midi = SoundDevices.MIDI_OUT.equals(d.kind);
                String said;
                if (midi) {
                    AppSettings.setMidiOut(d.key());
                    SoundOut.openDrumMidi(app);
                    said = "The drum tracks play through " + d.name;
                } else {
                    AppSettings.setSoundOut(d.key());
                    SoundOut.prefer(app.track);
                    said = "Pulsekit's sound plays through " + d.name;
                }
                SoundDevices.log(said, d.details);
                app.setNow(said);
                changed.run();
            });
        }
        if (SoundDevices.OUT.equals(d.kind) || SoundDevices.MIDI_OUT.equals(d.kind)) {
            b.setNeutralButton("Test", null);
        } else if (SoundDevices.IN.equals(d.kind)) {
            // An input's Test: a level meter, Record, Stop, Play and Save.
            b.setNeutralButton("Test", (x, w) -> InputTestScreen.show(app, d));
        }
        device = b.create();
        device.show();
        android.widget.Button test = device.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (test != null && !SoundDevices.IN.equals(d.kind)) {
            // Test leaves the device open: what it said is added below.
            test.setOnClickListener(v -> {
                test.setEnabled(false);
                SoundOut.test(app, d, said -> app.runOnUiThread(() -> {
                    lastTest = said;
                    test.setEnabled(true);
                    body.append("\n\n" + said);
                    SoundDevices.log("Test: " + d.name, said + "\n\n" + d.details);
                    app.setNow(said);
                }));
            });
        }
    }

    /** A level: its name and percent, and a slider (0 to `max` percent) that keeps what is set. */
    static LinearLayout level(final MainActivity app, final String name, String tag, int max, int now, final java.util.function.IntConsumer set) {
        LinearLayout box = app.col();
        box.setPadding(0, app.dp(6), 0, 0);
        final TextView label = app.text(name + ": " + now + "%", 13, false);
        label.setTag(tag + "-label");
        android.widget.SeekBar bar = new android.widget.SeekBar(app);
        bar.setMax(max);
        bar.setProgress(now);
        bar.setTag(tag);
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) {
                label.setText(name + ": " + p + "%");
                set.accept(p);
            }

            public void onStartTrackingTouch(android.widget.SeekBar s) {}

            public void onStopTrackingTouch(android.widget.SeekBar s) {
                app.setNow(name + " " + s.getProgress() + "%");
            }
        });
        box.addView(label);
        box.addView(bar);
        return box;
    }

    /** The drums back to Pulsekit's own sounds (no MIDI output). */
    static void useOwnDrums(MainActivity app) {
        AppSettings.setMidiOut("");
        AppSettings.setMidiOutPort(0);
        SoundOut.closeDrumMidi();
        SoundDevices.log("Drums back to Pulsekit's own sounds", SoundDevices.OWN_SYNTH);
        app.setNow("The drums play with Pulsekit's own sounds");
    }

    static String header(MainActivity app) {
        return "Pulsekit " + app.appVersion() + " (Android " + android.os.Build.VERSION.RELEASE + ", API " + android.os.Build.VERSION.SDK_INT + ", "
            + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + ")";
    }
}
