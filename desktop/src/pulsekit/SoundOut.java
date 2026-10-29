package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Port;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * The desktop's sound devices (Java Sound): the mixers (outputs, inputs, volume ports) with the
 * driver each comes through (DirectSound on Windows, ALSA on Linux, Core Audio on macOS), the
 * MIDI devices, the ASIO drivers Windows has installed (listed only: Java cannot open them), and
 * the driver files picked with Browse. The output picked is Pulsekit's: every clip and the MIDI
 * synth play through it (Java Sound's default lines are set to it).
 */
final class SoundOut {
    private SoundOut() {}

    static final String LINE_KEY = "javax.sound.sampled.SourceDataLine";
    static final String CLIP_KEY = "javax.sound.sampled.Clip";
    static final String DRIVERS_KEY = "soundDriverFiles";
    static final String ASIO_NOTE = "Listed from the Windows registry. Pulsekit plays through DirectSound or WASAPI-shared outputs (Java Sound); "
        + "ASIO itself needs a native bridge, not in Pulsekit yet. Most ASIO interfaces also have a DirectSound output listed above.";

    /** Java Sound's default lines go to the output picked (the mixer's name), or back to the system default. */
    static void apply() {
        String key = AppSettings.soundOut();
        String name = key.contains(" | ") ? key.substring(0, key.indexOf(" | ")) : key;
        if (name.isEmpty() || find(name) == null) {
            System.clearProperty(LINE_KEY);
            System.clearProperty(CLIP_KEY);
            return;
        }
        System.setProperty(LINE_KEY, "#" + name);
        System.setProperty(CLIP_KEY, "#" + name);
    }

    static Mixer.Info find(String name) {
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) if (mi.getName().equals(name)) return mi;
        return null;
    }

    /** Everything found now. */
    static List<SoundDevices.Device> list() {
        List<SoundDevices.Device> out = new ArrayList<SoundDevices.Device>();
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                Mixer m = AudioSystem.getMixer(mi);
                boolean plays = false, records = false, port = false;
                Set<String> playFormats = new LinkedHashSet<String>(), recFormats = new LinkedHashSet<String>();
                for (Line.Info li : m.getSourceLineInfo()) {
                    if (li instanceof Port.Info) port = true;
                    else if (li instanceof DataLine.Info && (SourceDataLine.class.isAssignableFrom(li.getLineClass()) || Clip.class.isAssignableFrom(li.getLineClass()))) {
                        plays = true;
                        for (AudioFormat f : ((DataLine.Info) li).getFormats()) playFormats.add(format(f));
                    }
                }
                for (Line.Info li : m.getTargetLineInfo()) {
                    if (li instanceof Port.Info) port = true;
                    else if (li instanceof DataLine.Info && TargetDataLine.class.isAssignableFrom(li.getLineClass())) {
                        records = true;
                        for (AudioFormat f : ((DataLine.Info) li).getFormats()) recFormats.add(format(f));
                    }
                }
                String driver = driverOf(m, mi);
                String about = "Vendor: " + mi.getVendor() + "\nDescription: " + mi.getDescription() + "\nVersion: " + mi.getVersion() + "\nJava class: " + m.getClass().getName();
                if (plays) out.add(new SoundDevices.Device(SoundDevices.OUT, mi.getName(), driver, about + "\nFormats: " + formats(playFormats)));
                if (records) out.add(new SoundDevices.Device(SoundDevices.IN, mi.getName(), driver, about + "\nFormats: " + formats(recFormats)));
                if (port && !plays && !records) out.add(new SoundDevices.Device(SoundDevices.PORTS, mi.getName(), driver, about));
            } catch (Throwable ex) {
                out.add(new SoundDevices.Device(SoundDevices.OUT, mi.getName(), "cannot be opened", String.valueOf(ex)));
            }
        }
        for (MidiDevice.Info mi : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice d = MidiSystem.getMidiDevice(mi);
                if (d instanceof Sequencer) continue;
                String kind = d instanceof Synthesizer ? SoundDevices.SYNTH : "MIDI port";
                String about = "Vendor: " + mi.getVendor() + "\nDescription: " + mi.getDescription() + "\nVersion: " + mi.getVersion() + "\nJava class: " + d.getClass().getName();
                if (d.getMaxReceivers() != 0) out.add(new SoundDevices.Device(SoundDevices.MIDI_OUT, mi.getName(), kind, about));
                if (d.getMaxTransmitters() != 0 && !(d instanceof Synthesizer)) out.add(new SoundDevices.Device(SoundDevices.MIDI_IN, mi.getName(), kind, about));
            } catch (Throwable ex) {
                out.add(new SoundDevices.Device(SoundDevices.MIDI_OUT, mi.getName(), "cannot be opened", String.valueOf(ex)));
            }
        }
        out.addAll(asioDrivers());
        for (String path : VstLists.kept(DRIVERS_KEY)) {
            File f = new File(path);
            out.add(new SoundDevices.Device(SoundDevices.DRIVERS, f.getName(), "picked with Browse",
                "File: " + path + "\n" + (f.isFile() ? "Size: " + f.length() + " bytes, changed " + new java.util.Date(f.lastModified()) : "Not found any more")
                    + "\nPulsekit does not load driver files itself: the driver serves its device's output above when it is installed."));
        }
        return out;
    }

    /** Which driver a mixer comes through, from its Java class and the system. */
    static String driverOf(Mixer m, Mixer.Info mi) {
        String c = m.getClass().getSimpleName();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean win = os.contains("win"), mac = os.contains("mac");
        if (c.contains("DirectAudio")) return win ? "DirectSound" : mac ? "Core Audio" : "ALSA";
        if (c.contains("PortMixer")) return win ? "Windows mixer ports" : mac ? "Core Audio ports" : "ALSA mixer ports";
        if (c.contains("SoftMixing")) return "Java software mixer";
        String d = mi.getDescription() == null ? "" : mi.getDescription();
        if (d.toLowerCase(Locale.ROOT).contains("pulse")) return "PulseAudio";
        return c;
    }

    static String format(AudioFormat f) {
        String rate = f.getSampleRate() == AudioSystem.NOT_SPECIFIED ? "any rate" : Math.round(f.getSampleRate()) + " Hz";
        String bits = f.getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED ? "any bits" : f.getSampleSizeInBits() + "-bit";
        String ch = f.getChannels() == AudioSystem.NOT_SPECIFIED ? "any channels" : f.getChannels() == 1 ? "mono" : f.getChannels() == 2 ? "stereo" : f.getChannels() + " channels";
        return bits + " " + ch + " " + rate;
    }

    static String formats(Set<String> all) {
        if (all.isEmpty()) return "-";
        List<String> some = new ArrayList<String>(all);
        return some.size() > 8 ? String.join(", ", some.subList(0, 8)) + " (+" + (some.size() - 8) + " more)" : String.join(", ", some);
    }

    /** Windows' installed ASIO drivers (HKLM\SOFTWARE\ASIO), with their descriptions. */
    static List<SoundDevices.Device> asioDrivers() {
        List<SoundDevices.Device> out = new ArrayList<SoundDevices.Device>();
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return out;
        for (String root : new String[] {"HKLM\\SOFTWARE\\ASIO", "HKLM\\SOFTWARE\\WOW6432Node\\ASIO"}) {
            String said = run(java.util.Arrays.asList("reg", "query", root, "/s"));
            String name = null;
            StringBuilder about = new StringBuilder();
            for (String line : said.split("\r?\n")) {
                if (line.startsWith("HKEY_")) {
                    if (name != null) out.add(new SoundDevices.Device(SoundDevices.DRIVERS, name, "ASIO" + (root.contains("WOW") ? " (32-bit)" : ""), about + ASIO_NOTE));
                    String key = line.trim();
                    name = key.equalsIgnoreCase(root.replace("HKLM", "HKEY_LOCAL_MACHINE")) ? null : key.substring(key.lastIndexOf('\\') + 1);
                    about.setLength(0);
                } else if (name != null && line.trim().length() > 0) {
                    about.append(line.trim().replaceAll("\\s+REG_SZ\\s+", ": ")).append('\n');
                }
            }
            if (name != null) out.add(new SoundDevices.Device(SoundDevices.DRIVERS, name, "ASIO" + (root.contains("WOW") ? " (32-bit)" : ""), about + ASIO_NOTE));
        }
        return out;
    }

    private static String run(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = p.getInputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return buf.toString("UTF-8");
        } catch (Exception ex) {
            return "";
        }
    }

    /** Test: a short tone through the output, a snare through the MIDI output, the level heard on an input. */
    static String test(SoundDevices.Device d) {
        try {
            if (SoundDevices.OUT.equals(d.kind)) return tone(d.name);
            if (SoundDevices.MIDI_OUT.equals(d.kind)) return midiNote(d.name);
            if (SoundDevices.IN.equals(d.kind)) return listen(d.name);
            return "Nothing to test for " + d.kind.toLowerCase(Locale.ROOT);
        } catch (Throwable ex) {
            return "Test failed on " + d.name + ": " + ex;
        }
    }

    static String tone(String name) throws Exception {
        Mixer.Info mi = find(name);
        if (mi == null) return name + " is not connected";
        AudioFormat f = new AudioFormat(44100, 16, 2, true, false);
        SourceDataLine line = (SourceDataLine) AudioSystem.getMixer(mi).getLine(new DataLine.Info(SourceDataLine.class, f));
        long t0 = System.currentTimeMillis();
        line.open(f);
        line.start();
        byte[] pcm = new byte[44100 * 4 * 6 / 10];
        for (int i = 0; i < pcm.length / 4; i++) {
            double env = Math.min(1, Math.min(i, pcm.length / 4 - i) / 2000.0);
            short v = (short) (8000 * env * Math.sin(2 * Math.PI * 440 * i / 44100.0));
            for (int c = 0; c < 2; c++) {
                pcm[4 * i + 2 * c] = (byte) v;
                pcm[4 * i + 2 * c + 1] = (byte) (v >> 8);
            }
        }
        line.write(pcm, 0, pcm.length);
        line.drain();
        String said = "Played a 440 Hz tone (0.6 s) through " + name + " as " + format(line.getFormat()) + ", buffer " + line.getBufferSize() + " bytes, took "
            + (System.currentTimeMillis() - t0) + " ms";
        line.close();
        return said;
    }

    static String midiNote(String name) throws Exception {
        for (MidiDevice.Info mi : MidiSystem.getMidiDeviceInfo()) {
            if (!mi.getName().equals(name)) continue;
            MidiDevice d = MidiSystem.getMidiDevice(mi);
            if (d instanceof Sequencer || d.getMaxReceivers() == 0) continue;
            boolean opened = !d.isOpen();
            if (opened) d.open();
            Receiver r = d.getReceiver();
            r.send(new ShortMessage(ShortMessage.NOTE_ON, 9, 38, 110), -1);
            Thread.sleep(300);
            r.send(new ShortMessage(ShortMessage.NOTE_OFF, 9, 38, 0), -1);
            r.close();
            if (opened) d.close();
            return "Sent a snare (note 38, channel 10) to " + name;
        }
        return name + " is not connected";
    }

    static String listen(String name) throws Exception {
        Mixer.Info mi = find(name);
        if (mi == null) return name + " is not connected";
        AudioFormat f = new AudioFormat(44100, 16, 1, true, false);
        TargetDataLine line = (TargetDataLine) AudioSystem.getMixer(mi).getLine(new DataLine.Info(TargetDataLine.class, f));
        line.open(f);
        line.start();
        byte[] buf = new byte[44100 * 2];
        int got = 0;
        long end = System.currentTimeMillis() + 1500;
        while (got < buf.length && System.currentTimeMillis() < end) got += line.read(buf, got, Math.min(4096, buf.length - got));
        line.stop();
        line.close();
        int peak = 0;
        for (int i = 0; i + 1 < got; i += 2) peak = Math.max(peak, Math.abs((short) ((buf[i] & 0xff) | (buf[i + 1] << 8))));
        return String.format(Locale.ROOT, "Listened 1 s on %s: peak %.1f%% (%d samples)", name, 100.0 * peak / 32768, got / 2);
    }

    /** The MIDI output picked (null: Pulsekit's own synth, or it is not connected). */
    static MidiDevice midiOut() {
        String key = AppSettings.midiOut();
        if (key.isEmpty()) return null;
        String name = key.contains(" | ") ? key.substring(0, key.indexOf(" | ")) : key;
        try {
            for (MidiDevice.Info mi : MidiSystem.getMidiDeviceInfo()) {
                if (!mi.getName().equals(name)) continue;
                MidiDevice d = MidiSystem.getMidiDevice(mi);
                if (!(d instanceof Sequencer) && !(d instanceof Synthesizer) && d.getMaxReceivers() != 0) return d;
            }
        } catch (Exception ignored) {
            // the own synth
        }
        return null;
    }

    /** The system's sound settings: Windows' Sound control panel, macOS's Sound, a Linux mixer. */
    static String openSystemSettings() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<List<String>> tries = new ArrayList<List<String>>();
        if (os.contains("win")) tries.add(java.util.Arrays.asList("control", "mmsys.cpl"));
        else if (os.contains("mac")) tries.add(java.util.Arrays.asList("open", "/System/Library/PreferencePanes/Sound.prefPane"));
        else {
            tries.add(java.util.Arrays.asList("pavucontrol"));
            tries.add(java.util.Arrays.asList("gnome-control-center", "sound"));
            tries.add(java.util.Arrays.asList("systemsettings", "kcm_pulseaudio"));
        }
        for (List<String> cmd : tries) {
            try {
                new ProcessBuilder(cmd).start();
                return "Opened " + String.join(" ", cmd);
            } catch (Exception ignored) {
                // the next one
            }
        }
        return "No sound settings program found (tried " + tries.size() + ")";
    }
}
