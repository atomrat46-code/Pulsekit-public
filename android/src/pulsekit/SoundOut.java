package pulsekit;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The phone's sound devices: outputs and inputs (USB audio interfaces and headsets through OTG,
 * Bluetooth, wired headphones, the speaker), MIDI devices (USB, Bluetooth, virtual) and the USB
 * devices plugged in. Android has the drivers for class-compliant USB audio and MIDI built in;
 * the output picked is preferred by Pulsekit's players (the drum mixer, MediaPlayer).
 */
final class SoundOut {
    private SoundOut() {}

    static final String ANDROID_DRIVERS = "Android's own USB audio and MIDI";
    static final String DRIVERS_NOTE = "Android drives class-compliant USB audio interfaces and MIDI keyboards itself (OTG cable or USB-C): "
        + "no driver is installed for them. A device that needs its maker's driver on a computer works on Android only when it has a class-compliant mode.";

    /** The app's context, for the players that are made without one at hand. */
    static Context ctx;

    static void init(Context c) {
        ctx = c.getApplicationContext() != null ? c.getApplicationContext() : c;
    }

    static List<SoundDevices.Device> list(Context c) {
        List<SoundDevices.Device> out = new ArrayList<SoundDevices.Device>();
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) out.add(device(SoundDevices.OUT, d));
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) out.add(device(SoundDevices.IN, d));
        }
        if (c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            MidiManager mm = (MidiManager) c.getSystemService(Context.MIDI_SERVICE);
            if (mm != null) {
                for (MidiDeviceInfo info : mm.getDevices()) {
                    String name = midiName(info);
                    String about = "Manufacturer: " + info.getProperties().getString(MidiDeviceInfo.PROPERTY_MANUFACTURER, "-") + "\nProduct: "
                        + info.getProperties().getString(MidiDeviceInfo.PROPERTY_PRODUCT, "-") + "\nId: " + info.getId() + "\nInput ports: " + info.getInputPortCount()
                        + ", output ports: " + info.getOutputPortCount();
                    // Pulsekit sends to a device's input port: that device is a MIDI output for Pulsekit.
                    if (info.getInputPortCount() > 0) out.add(new SoundDevices.Device(SoundDevices.MIDI_OUT, name, midiType(info.getType()), about));
                    if (info.getOutputPortCount() > 0) out.add(new SoundDevices.Device(SoundDevices.MIDI_IN, name, midiType(info.getType()), about));
                }
            }
        } else {
            out.add(new SoundDevices.Device(SoundDevices.MIDI_OUT, "No MIDI on this phone", "", "Android reports no MIDI support (android.software.midi)."));
        }
        out.add(new SoundDevices.Device(SoundDevices.DRIVERS, ANDROID_DRIVERS, "built in", DRIVERS_NOTE));
        UsbManager um = (UsbManager) c.getSystemService(Context.USB_SERVICE);
        if (um != null) {
            for (UsbDevice u : um.getDeviceList().values()) {
                StringBuilder classes = new StringBuilder();
                for (int i = 0; i < u.getInterfaceCount(); i++) {
                    int cls = u.getInterface(i).getInterfaceClass(), sub = u.getInterface(i).getInterfaceSubclass();
                    classes.append(i == 0 ? "" : ", ").append(cls == 1 ? (sub == 3 ? "MIDI" : sub == 2 ? "audio streaming" : "audio control") : cls == 3 ? "HID" : "class " + cls);
                }
                String name = u.getProductName() != null ? u.getProductName() : u.getDeviceName();
                out.add(new SoundDevices.Device(SoundDevices.USB, name, u.getManufacturerName() == null ? "" : u.getManufacturerName(),
                    String.format(Locale.ROOT, "Vendor id %04x, product id %04x\nInterfaces: %s\nPath: %s", u.getVendorId(), u.getProductId(), classes, u.getDeviceName())));
            }
        }
        return out;
    }

    static SoundDevices.Device device(String kind, AudioDeviceInfo d) {
        StringBuilder about = new StringBuilder("Id: " + d.getId());
        if (Build.VERSION.SDK_INT >= 28 && d.getAddress() != null && d.getAddress().length() > 0) about.append("\nAddress: ").append(d.getAddress());
        about.append("\nSample rates: ").append(d.getSampleRates().length == 0 ? "any" : java.util.Arrays.toString(d.getSampleRates()));
        about.append("\nChannels: ").append(d.getChannelCounts().length == 0 ? "any" : java.util.Arrays.toString(d.getChannelCounts()));
        about.append("\nEncodings: ").append(d.getEncodings().length == 0 ? "any" : encodings(d.getEncodings()));
        return new SoundDevices.Device(kind, String.valueOf(d.getProductName()), typeName(d.getType()), about.toString());
    }

    static String encodings(int[] all) {
        StringBuilder sb = new StringBuilder();
        for (int e : all) sb.append(sb.length() == 0 ? "" : ", ").append(e == AudioFormat.ENCODING_PCM_16BIT ? "16-bit" : e == AudioFormat.ENCODING_PCM_FLOAT ? "float" : e == AudioFormat.ENCODING_PCM_8BIT ? "8-bit" : "encoding " + e);
        return sb.toString();
    }

    static String typeName(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "built-in speaker";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "earpiece";
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "built-in microphone";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "wired headphones";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "wired headset";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB audio (OTG)";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "USB accessory";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB headset";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth (A2DP)";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth (calls)";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_LINE_ANALOG: return "line out";
            case AudioDeviceInfo.TYPE_TELEPHONY: return "telephony";
            default: return "type " + t;
        }
    }

    static String midiName(MidiDeviceInfo info) {
        String n = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
        if (n == null) n = info.getProperties().getString(MidiDeviceInfo.PROPERTY_PRODUCT);
        return n == null ? "MIDI device " + info.getId() : n;
    }

    static String midiType(int t) {
        return t == MidiDeviceInfo.TYPE_USB ? "USB MIDI" : t == MidiDeviceInfo.TYPE_BLUETOOTH ? "Bluetooth MIDI" : t == MidiDeviceInfo.TYPE_VIRTUAL ? "virtual MIDI" : "MIDI";
    }

    /** The output picked, when it is connected now. */
    static AudioDeviceInfo chosen(Context c) {
        String key = AppSettings.soundOut();
        if (key.isEmpty() || c == null) return null;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return null;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) if (device(SoundDevices.OUT, d).key().equals(key)) return d;
        return null;
    }

    /** A player plays through the output picked (the system's routing when none, or it is unplugged). */
    static void prefer(Object player) {
        try {
            AudioDeviceInfo d = chosen(ctx);
            if (player instanceof AudioTrack) ((AudioTrack) player).setPreferredDevice(d);
            else if (player instanceof MediaPlayer && Build.VERSION.SDK_INT >= 28) ((MediaPlayer) player).setPreferredDevice(d);
        } catch (Throwable ignored) {
            // the system's routing
        }
    }

    /** Test: a 440 Hz tone through the output, a snare to the MIDI output. */
    static void test(final Context c, final SoundDevices.Device d, final java.util.function.Consumer<String> said) {
        if (SoundDevices.OUT.equals(d.kind)) {
            new Thread(() -> said.accept(tone(c, d)), "sound-test").start();
            return;
        }
        if (SoundDevices.MIDI_OUT.equals(d.kind)) {
            midiNote(c, d, said);
            return;
        }
        said.accept("Nothing to test for " + d.kind.toLowerCase(Locale.ROOT) + (SoundDevices.IN.equals(d.kind) ? " (listening needs the microphone permission, which Pulsekit does not ask)" : ""));
    }

    static String tone(Context c, SoundDevices.Device d) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo target = null;
            for (AudioDeviceInfo x : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) if (device(SoundDevices.OUT, x).key().equals(d.key())) target = x;
            if (target == null) return d.name + " is not connected";
            int rate = 44100, n = rate * 6 / 10;
            short[] pcm = new short[n * 2];
            for (int i = 0; i < n; i++) {
                double env = Math.min(1, Math.min(i, n - i) / 2000.0);
                pcm[2 * i] = pcm[2 * i + 1] = (short) (8000 * env * Math.sin(2 * Math.PI * 440 * i / rate));
            }
            AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, pcm.length * 2, AudioTrack.MODE_STATIC);
            t.write(pcm, 0, pcm.length);
            boolean preferred = t.setPreferredDevice(target);
            long t0 = System.currentTimeMillis();
            t.play();
            Thread.sleep(700);
            AudioDeviceInfo routed = t.getRoutedDevice();
            t.stop();
            t.release();
            return "Played a 440 Hz tone (0.6 s) for " + d.name + (preferred ? "" : " (Android did not take the preference)") + ", routed to "
                + (routed == null ? "-" : routed.getProductName() + " (" + typeName(routed.getType()) + ")") + ", " + (System.currentTimeMillis() - t0) + " ms";
        } catch (Throwable ex) {
            return "Test failed on " + d.name + ": " + ex;
        }
    }

    static void midiNote(Context c, final SoundDevices.Device d, final java.util.function.Consumer<String> said) {
        try {
            MidiManager mm = (MidiManager) c.getSystemService(Context.MIDI_SERVICE);
            MidiDeviceInfo found = null;
            if (mm != null) for (MidiDeviceInfo info : mm.getDevices()) if (midiName(info).equals(d.name) && info.getInputPortCount() > 0) found = info;
            if (found == null) {
                said.accept(d.name + " is not connected");
                return;
            }
            mm.openDevice(found, device -> {
                if (device == null) {
                    said.accept("Could not open " + d.name);
                    return;
                }
                try {
                    MidiInputPort port = device.openInputPort(0);
                    port.send(new byte[] {(byte) 0x99, 38, 110}, 0, 3);
                    Thread.sleep(300);
                    port.send(new byte[] {(byte) 0x89, 38, 0}, 0, 3);
                    port.close();
                    device.close();
                    said.accept("Sent a snare (note 38, channel 10) to " + d.name);
                } catch (Exception ex) {
                    said.accept("Test failed on " + d.name + ": " + ex);
                }
            }, new android.os.Handler(android.os.Looper.getMainLooper()));
        } catch (Throwable ex) {
            said.accept("Test failed on " + d.name + ": " + ex);
        }
    }

    /** The drum MIDI output's open port (null: Pulsekit's own sounds play the drums). */
    static volatile MidiInputPort drumPort;
    static android.media.midi.MidiDevice drumDevice;

    /** Opens the MIDI output picked for the drum tracks (closing the one before); none: the own sounds. */
    static void openDrumMidi(final Context c) {
        closeDrumMidi();
        final String key = AppSettings.midiOut();
        if (key.isEmpty() || c == null || !c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MIDI)) return;
        try {
            MidiManager mm = (MidiManager) c.getSystemService(Context.MIDI_SERVICE);
            MidiDeviceInfo found = null;
            if (mm != null) {
                for (MidiDeviceInfo info : mm.getDevices()) {
                    if (info.getInputPortCount() > 0 && (midiName(info) + " | " + midiType(info.getType())).equals(key)) found = info;
                }
            }
            if (found == null) {
                SoundDevices.log("Drum MIDI output not connected", key + ": Pulsekit's own sounds play the drums");
                return;
            }
            mm.openDevice(found, device -> {
                if (device == null) {
                    SoundDevices.log("Drum MIDI output not opened", key);
                    return;
                }
                drumDevice = device;
                drumPort = device.openInputPort(0);
                SoundDevices.log("Drum MIDI output opened", key + (drumPort == null ? ": its input port 0 did not open" : ""));
            }, new android.os.Handler(android.os.Looper.getMainLooper()));
        } catch (Throwable ex) {
            SoundDevices.log("Drum MIDI output not opened", key + ": " + ex);
        }
    }

    static void closeDrumMidi() {
        try {
            if (drumPort != null) drumPort.close();
            if (drumDevice != null) drumDevice.close();
        } catch (Exception ignored) {
            // closed
        }
        drumPort = null;
        drumDevice = null;
    }

    /** A drum hit to the MIDI output picked (channel 10, its note off 100 ms later); false: none is open. */
    static boolean drumHit(int note, int velocity) {
        MidiInputPort port = drumPort;
        if (port == null) return false;
        try {
            port.send(new byte[] {(byte) 0x99, (byte) note, (byte) velocity}, 0, 3);
            port.send(new byte[] {(byte) 0x89, (byte) note, 0}, 0, 3, System.nanoTime() + 100000000L);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
