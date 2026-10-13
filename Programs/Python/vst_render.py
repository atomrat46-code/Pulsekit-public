"""
vst_render.py: plays a MIDI file through a VST2 or VST3 instrument and saves what it sounds like as a WAV.

Usage: python vst_render.py [song.mid] [output.wav] [--plugin <plugin>] [--state <file>] [--wav <input.wav>] [--effect <plugin>] [--effect_state <file>] [--rate N] [--tail N] [--volume N] [--bits N] [--edit] [--scan]

  song.mid        the MIDI played (every channel; a drum plugin hears the drum channel as it is).
  output.wav      the WAV made; <song>-<plugin>.wav when it is not given.
  --plugin        the instrument: a VST2 (.dll, .so, .vst) or VST3 (.vst3) plugin. With --wav, an
                  effect (an amp, a reverb) that the WAV is played through instead.
  --state         the plugin's settings (its kit, patch, mixer): a file --edit saved, a .fxp/.fxb
                  (VST2) or a .vstpreset (VST3). Without it, the settings last saved with --edit for
                  this plugin are used (~/.pulsekit/vst/<plugin>.state), else the plugin's defaults.
  --wav           a WAV to play through --plugin (an effect) instead of MIDI.
  --effect        an effect after the instrument (an amp sim, a reverb); --effect_state its settings,
                  as --state (its last --edit settings when not given).
  --rate N        sample rate: 44100 (default) or 48000.
  --tail N        seconds rendered after the last note, for the sound to ring out (2).
  --volume N      percent, 1 to 400 (100). A mix that would clip is turned down to fit.
  --bits N        16 (default) or 24-bit WAV.
  --edit          opens the plugin's own window first (Windows and Mac): load a kit or patch there,
                  close the window, and its settings are saved (to --state, else
                  ~/.pulsekit/vst/<plugin>.state) and used for this and later runs. With --effect,
                  the effect's window opens after the instrument's.
  --scan          lists the plugins in the usual VST folders, then stops.

Carla (free) can be the plugin too: its Carla-Rack or Carla-Patchbay VST hosts other plugins
(also LV2, SFZ and SoundFonts); set it up with --edit.

Needs DawDreamer and numpy (pip install dawdreamer), on Windows, Mac or Linux (64-bit Python 3.9+).
A plugin is a desktop program: this does not run on a phone. Licensed plugins render as they do in a
DAW once they are installed and activated on the computer.
"""
import json
import os
import struct
import sys
import wave

PLUGIN_ENDS = (".vst3", ".dll", ".so", ".vst", ".component")


def fail(message, code=1):
    print("Failed: " + message)
    sys.exit(code)


def vst_folders():
    """The usual VST2 and VST3 folders of this computer that exist."""
    home = os.path.expanduser("~")
    if sys.platform.startswith("win"):
        pf = os.environ.get("ProgramFiles", r"C:\Program Files")
        common = os.environ.get("CommonProgramFiles", os.path.join(pf, "Common Files"))
        dirs = [os.path.join(common, "VST3"), os.path.join(common, "VST2"), os.path.join(common, "Steinberg", "VST2"),
                os.path.join(pf, "VSTPlugins"), os.path.join(pf, "Steinberg", "VSTPlugins"), os.path.join(pf, "VST2"),
                os.path.join(pf, "VST")]
    elif sys.platform == "darwin":
        dirs = ["/Library/Audio/Plug-Ins/VST3", "/Library/Audio/Plug-Ins/VST",
                os.path.join(home, "Library/Audio/Plug-Ins/VST3"), os.path.join(home, "Library/Audio/Plug-Ins/VST")]
    else:
        dirs = [os.path.join(home, ".vst3"), os.path.join(home, ".vst"), "/usr/lib/vst3", "/usr/lib/vst",
                "/usr/local/lib/vst3", "/usr/local/lib/vst"]
    return [d for d in dirs if os.path.isdir(d)]


def scan():
    found = 0
    for d in vst_folders():
        print(d)
        for root, subdirs, files in os.walk(d):
            names = [n for n in subdirs + files if n.lower().endswith(PLUGIN_ENDS)]
            for n in sorted(names):
                print("  " + os.path.relpath(os.path.join(root, n), d))
                found += 1
            # A .vst3 or .vst bundle is one plugin: its insides are not listed.
            subdirs[:] = [s for s in subdirs if not s.lower().endswith(PLUGIN_ENDS)]
    if found == 0:
        print("No plugins in the usual VST folders. Give the plugin's file with --plugin.")
    else:
        print("Succeeded: %d plugin%s" % (found, "" if found == 1 else "s"))


def stem(path):
    base = os.path.basename(os.path.normpath(path))
    for end in PLUGIN_ENDS + (".mid", ".midi", ".wav", ".wave"):
        if base.lower().endswith(end):
            return base[:-len(end)]
    return os.path.splitext(base)[0]


def state_home(plugin):
    return os.path.join(os.path.expanduser("~"), ".pulsekit", "vst", stem(plugin) + ".state")


def var_len(d, p):
    v = 0
    while p < len(d):
        b = d[p]
        p += 1
        v = (v << 7) | (b & 0x7F)
        if b < 0x80:
            break
    return v, p


def midi_length(path):
    """The time of the MIDI file's last note-off (or event) in seconds, following tempo changes, and its note count."""
    d = open(path, "rb").read()
    if d[:4] != b"MThd":
        fail("%s is not a MIDI file" % os.path.basename(path))
    ppq = struct.unpack(">H", d[12:14])[0]
    if ppq & 0x8000:
        fail("SMPTE-timed MIDI files are not supported")
    ppq = ppq or 480
    i = 8 + struct.unpack(">I", d[4:8])[0]
    events, tempos, notes = [], [], 0
    while i + 8 <= len(d):
        kind, size = d[i:i + 4], struct.unpack(">I", d[i + 4:i + 8])[0]
        p, end, tick, status = i + 8, min(len(d), i + 8 + size), 0, 0
        while kind == b"MTrk" and p < end:
            delta, p = var_len(d, p)
            tick += delta
            b = d[p]
            if b == 0xFF:
                t = d[p + 1]
                n, p = var_len(d, p + 2)
                if t == 0x51 and n == 3:
                    tempos.append((tick, (d[p] << 16) | (d[p + 1] << 8) | d[p + 2]))
                p += n
                events.append(tick)
                continue
            if b in (0xF0, 0xF7):
                n, p = var_len(d, p + 1)
                p += n
                continue
            if b & 0x80:
                status = b
                p += 1
            hi = status & 0xF0
            if hi in (0xC0, 0xD0):
                p += 1
            else:
                if hi == 0x90 and p + 1 < len(d) and d[p + 1] > 0:
                    notes += 1
                p += 2
            events.append(tick)
        i += 8 + size
    last = max(events) if events else 0
    tempos.sort()
    secs, at, us = 0.0, 0, 500000
    for tick, t in tempos:
        if tick >= last:
            break
        secs += (tick - at) * us / 1e6 / ppq
        at, us = tick, t
    secs += (last - at) * us / 1e6 / ppq
    return secs, notes


def read_wav(path, rate):
    import numpy as np
    w = wave.open(path, "rb")
    ch, width, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
    raw = w.readframes(n)
    w.close()
    if width == 2:
        a = np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0
    elif width == 3:
        b = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3).astype(np.int32)
        v = b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16)
        a = (np.where(v >= 1 << 23, v - (1 << 24), v) / float(1 << 23)).astype(np.float32)
    elif width == 4:
        a = np.frombuffer(raw, dtype="<i4").astype(np.float32) / 2147483648.0
    else:
        a = (np.frombuffer(raw, dtype=np.uint8).astype(np.float32) - 128) / 128.0
    a = a.reshape(-1, ch).T
    if sr != rate:
        # Linear resampling to the render's rate.
        m = int(round(a.shape[1] * rate / float(sr)))
        x = np.linspace(0, a.shape[1] - 1, m)
        a = np.stack([np.interp(x, np.arange(a.shape[1]), c) for c in a]).astype(np.float32)
    if a.shape[0] == 1:
        a = np.vstack([a, a])
    return a[:2], a.shape[1] / float(rate)


def write_wav(path, audio, rate, bits):
    import numpy as np
    a = np.clip(audio, -1.0, 1.0).T
    w = wave.open(path, "wb")
    w.setnchannels(a.shape[1])
    w.setframerate(rate)
    if bits == 24:
        v = np.round(a * 8388607.0).astype(np.int32).reshape(-1)
        b = np.empty((v.size, 3), dtype=np.uint8)
        b[:, 0] = v & 0xFF
        b[:, 1] = (v >> 8) & 0xFF
        b[:, 2] = (v >> 16) & 0xFF
        w.setsampwidth(3)
        w.writeframes(b.tobytes())
    else:
        w.setsampwidth(2)
        w.writeframes(np.round(a * 32767.0).astype("<i2").tobytes())
    w.close()


def apply_state(proc, plugin, given, label):
    """Loads the settings into the plugin; returns the line saying which."""
    path = given or state_home(plugin)
    if not given and not os.path.isfile(path):
        return "%s: the plugin's default settings" % label
    if not os.path.isfile(path):
        fail("no settings file %s" % path)
    low = path.lower()
    if low.endswith((".fxp", ".fxb")):
        ok = proc.load_preset(path)
    elif low.endswith(".vstpreset"):
        ok = proc.load_vst3_preset(path)
    else:
        try:
            proc.load_state(path)
            ok = True
        except RuntimeError as e:
            # DawDreamer opens the plugin's window to load its state: without a desktop (a server),
            # the parameter values saved beside it are set instead (not a sampler's kit or samples).
            values = path + ".params.json"
            if not os.path.isfile(values):
                fail("could not load the settings in %s (%s)" % (os.path.basename(path), e))
            proc.set_patch([(int(i), float(v)) for i, v in json.load(open(values))])
            return "%s: parameter values %s (Note: the full settings need a desktop: %s)" % (label, values, e)
    if ok is False:
        fail("the plugin did not take the settings in %s" % os.path.basename(path))
    return "%s: settings %s" % (label, path)


def edit(proc, plugin, given, label):
    """Opens the plugin's window; when it is closed, saves its settings. Returns the file saved."""
    path = given or state_home(plugin)
    if path.lower().endswith((".fxp", ".fxb", ".vstpreset")):
        path = os.path.splitext(path)[0] + ".state"
    print("%s: its window is open. Load a kit or patch, then close the window." % label)
    sys.stdout.flush()
    try:
        proc.open_editor()
    except Exception as e:
        fail("the plugin's window could not open (%s). --edit works on Windows and Mac." % e)
    folder = os.path.dirname(path)
    if folder and not os.path.isdir(folder):
        os.makedirs(folder)
    proc.save_state(path)
    # Its parameter values too, for a computer without a desktop (apply_state).
    with open(path + ".params.json", "w") as f:
        json.dump([[int(i), float(v)] for i, v in proc.get_patch()], f)
    print("Wrote %s settings: %s" % (label, path))
    return path


def load_plugin(engine, name, path):
    if not os.path.exists(path):
        fail("no plugin at %s" % path)
    try:
        return engine.make_plugin_processor(name, os.path.abspath(path))
    except Exception as e:
        fail("could not load %s (%s). Is it a 64-bit VST2 or VST3 plugin for this computer?" % (os.path.basename(path), e))


def main(argv):
    opts = {"--plugin": None, "--state": None, "--wav": None, "--effect": None, "--effect_state": None,
            "--rate": "44100", "--tail": "2", "--volume": "100", "--bits": "16"}
    switches = set()
    files = []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a in opts:
            if i + 1 >= len(argv):
                fail("%s needs a value" % a)
            opts[a] = argv[i + 1]
            i += 2
            continue
        if a in ("--edit", "--scan"):
            switches.add(a)
        elif a.startswith("--"):
            fail("unknown switch %s" % a)
        else:
            files.append(a)
        i += 1
    if "--scan" in switches:
        scan()
        return
    midi = next((f for f in files if f.lower().endswith((".mid", ".midi"))), None)
    out = next((f for f in files if f.lower().endswith((".wav", ".wave"))), None)
    plugin = opts["--plugin"]
    if not plugin:
        fail("no plugin: give one with --plugin (--scan lists the installed ones)")
    if not midi and not opts["--wav"]:
        fail("no MIDI file to play (or a WAV with --wav)")
    try:
        rate = int(opts["--rate"])
        tail = float(opts["--tail"])
        volume = float(opts["--volume"])
        bits = int(opts["--bits"])
    except ValueError:
        fail("--rate, --tail, --volume and --bits take numbers")
    if rate not in (44100, 48000, 88200, 96000):
        fail("--rate is 44100 or 48000 (or 88200, 96000)")
    if not 1 <= volume <= 400:
        fail("--volume is 1 to 400")
    if bits not in (16, 24):
        fail("--bits is 16 or 24")
    tail = max(0.0, min(30.0, tail))
    try:
        import numpy as np
        import dawdreamer as daw
    except ImportError as e:
        fail("DawDreamer is not installed (%s). Install it with: pip install dawdreamer" % e, 2)

    engine = daw.RenderEngine(rate, 512)
    main_proc = load_plugin(engine, "plugin", plugin)
    print("Plugin: %s (%d in, %d out)" % (os.path.basename(os.path.normpath(plugin)), main_proc.get_num_input_channels(), main_proc.get_num_output_channels()))
    if "--edit" in switches:
        opts["--state"] = edit(main_proc, plugin, opts["--state"], "Plugin")
    else:
        print(apply_state(main_proc, plugin, opts["--state"], "Plugin"))
    graph = []
    if opts["--wav"]:
        if not os.path.isfile(opts["--wav"]):
            fail("no WAV at %s" % opts["--wav"])
        data, length = read_wav(opts["--wav"], rate)
        play = engine.make_playback_processor("input", data)
        graph.append((play, []))
        graph.append((main_proc, ["input"]))
        source = opts["--wav"]
        print("Input: %s, %.1f s" % (os.path.basename(source), length))
    else:
        if not os.path.isfile(midi):
            fail("no MIDI file at %s" % midi)
        length, notes = midi_length(midi)
        if notes == 0:
            fail("%s has no notes" % os.path.basename(midi))
        if not main_proc.load_midi(os.path.abspath(midi), clear_previous=True, beats=False, all_events=True):
            fail("the plugin could not take %s" % os.path.basename(midi))
        graph.append((main_proc, []))
        source = midi
        print("MIDI: %s, %d notes, %.1f s" % (os.path.basename(midi), notes, length))
    last = "plugin"
    effect = opts["--effect"]
    if effect:
        fx = load_plugin(engine, "effect", effect)
        print("Effect: %s" % os.path.basename(os.path.normpath(effect)))
        if "--edit" in switches:
            edit(fx, effect, opts["--effect_state"], "Effect")
        else:
            print(apply_state(fx, effect, opts["--effect_state"], "Effect"))
        graph.append((fx, [last]))
    if not engine.load_graph(graph):
        fail("the plugins could not be connected")
    seconds = length + tail
    engine.render(seconds)
    audio = np.asarray(engine.get_audio(), dtype=np.float32)
    if audio.ndim == 1:
        audio = audio[None, :]
    if audio.shape[0] == 1:
        audio = np.vstack([audio, audio])
    audio = audio[:2] * (volume / 100.0)
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    if peak < 1e-6:
        print("Note: the render is silent. Does the plugin need a kit or patch loaded (--edit), or is it an effect (--wav)?")
    elif peak > 1.0:
        audio = audio / peak * 0.999
        print("Note: turned down %.1f dB so it does not clip" % (20 * np.log10(peak / 0.999)))
    if not out:
        out = "%s-%s.wav" % (stem(source), stem(plugin))
    write_wav(out, audio, rate, bits)
    print("Wrote %s: %.1f s, %d Hz, %d-bit stereo" % (out, audio.shape[1] / float(rate), rate, bits))
    print("Succeeded: %s" % out)


if __name__ == "__main__":
    main(sys.argv[1:])
