"""
vst_render.py: plays a MIDI file through a VST2 or VST3 instrument and saves what it sounds like as a WAV.

Usage: python vst_render.py [song.mid] [output.wav] [--plugin <plugin>] [--state <file>] [--wav <input.wav>] [--effect <plugin>] [--effect_state <file>] [--rate N] [--tail N] [--volume N] [--bits N] [--edit] [--dir <folder>] [--recursive] [--outdir <folder>] [--test] [--scan]

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
  --dir           renders every MIDI file in the folder (the plugin loads once): <midi>-<plugin>.wav
                  beside each, or in --outdir (its subfolders kept). --recursive: subfolders too.
  --test          then runs DrumMidi_CRT on each WAV and compares its hits with the MIDI played, per
                  drum family (within 30 ms, after the best shift): a report, vst-test-<plugin>.txt,
                  and DrumMidi's MIDI as <wav>-dm.mid. Pulsekit gives its DrumMidi_CRT.jar (--drummidi)
                  and Java (--java).
  --scan          lists the plugins in the usual VST folders, then stops.

The plugins' own delay (lookahead, oversampling) is taken off the start, so the hits land where
the MIDI has them.

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


def midi_notes(path):
    """The MIDI file's length (its last event) in seconds and its notes as (seconds, note), following tempo changes."""
    d = open(path, "rb").read()
    if d[:4] != b"MThd":
        raise ValueError("%s is not a MIDI file" % os.path.basename(path))
    ppq = struct.unpack(">H", d[12:14])[0]
    if ppq & 0x8000:
        raise ValueError("SMPTE-timed MIDI files are not supported")
    ppq = ppq or 480
    i = 8 + struct.unpack(">I", d[4:8])[0]
    events, tempos, ons = [], [], []
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
                    ons.append((tick, d[p]))
                p += 2
            events.append(tick)
        i += 8 + size
    tempos.sort()

    def seconds(tick):
        secs, at, us = 0.0, 0, 500000
        for t, v in tempos:
            if t >= tick:
                break
            secs += (t - at) * us / 1e6 / ppq
            at, us = t, v
        return secs + (tick - at) * us / 1e6 / ppq

    last = max(events) if events else 0
    return seconds(last), sorted((seconds(t), n) for t, n in ons)


# Drum families for --test, as Pulsekit's CompareHits groups them (hats, rides and crashes also together).
FAMILIES = (("Kick", (35, 36)), ("Snare", (37, 38, 39, 40)), ("Hats", (42, 44, 46)),
            ("Ride", (51, 53, 59)), ("Crash", (49, 52, 55, 57)), ("Toms", (41, 43, 45, 47, 48, 50)),
            ("Cymbals", (42, 44, 46, 49, 51, 52, 53, 55, 57, 59)))
TOLERANCE = 0.03


def matched(truth, found, shift):
    """How many found hits are within TOLERANCE of a played hit (each played hit used once), with found shifted."""
    import bisect
    used, ok = set(), 0
    for x in found:
        x += shift
        j = bisect.bisect_left(truth, x - TOLERANCE)
        while j < len(truth) and truth[j] <= x + TOLERANCE:
            if j not in used:
                used.add(j)
                ok += 1
                break
            j += 1
    return ok


def score(played, found, end):
    """Per family (played, found, matched), after the best shift of DrumMidi's hits within 100 ms; and the shift."""
    played = [(t, n) for t, n in played if t < end]
    every_t = sorted(t for t, n in played)
    every_f = sorted(t for t, n in found)
    shift = max((matched(every_t, every_f, s / 1000.0), -abs(s), s / 1000.0) for s in range(-100, 101, 2))[2]
    rows = []
    for name, notes in FAMILIES:
        t = sorted(x for x, n in played if n in notes)
        f = sorted(x for x, n in found if n in notes)
        rows.append((name, len(t), len(f), matched(t, f, shift)))
    return rows, shift


def score_line(rows):
    parts = []
    for name, played, found, ok in rows:
        if played == 0 and found == 0:
            continue
        parts.append("%s %d/%d P%3.0f R%3.0f" % (name, ok, played, 100.0 * ok / max(1, found), 100.0 * ok / max(1, played)))
    return "  ".join(parts)


def drum_midi(java, jar, wav, mid):
    """DrumMidi_CRT on the WAV; returns its notes, or an error text."""
    import subprocess
    try:
        # DrumMidi_CRT writes into its working folder: run it in the WAV's.
        where = os.path.dirname(os.path.abspath(mid))
        r = subprocess.run([java, "-cp", os.path.abspath(jar), "DrumMidi_CRT", os.path.abspath(wav), os.path.basename(mid)],
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=1800, cwd=where)
    except Exception as e:
        return "DrumMidi_CRT did not run (%s)" % e
    if r.returncode != 0 or not os.path.isfile(mid):
        tail = r.stdout.decode("utf-8", "replace").strip().splitlines()[-1:] or [""]
        return "DrumMidi_CRT failed (%s)" % tail[0]
    return midi_notes(mid)[1]


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


def collect(folder, recursive):
    """The MIDI files in the folder (and its subfolders), not DrumMidi's own (*-dm.mid) from a --test."""
    found = []
    for root, dirs, names in os.walk(folder):
        dirs.sort()
        for n in sorted(names):
            if n.lower().endswith((".mid", ".midi")) and not n.lower().endswith("-dm.mid"):
                found.append(os.path.join(root, n))
        if not recursive:
            break
    return found


class Chain(object):
    """The plugin (and the effect) loaded once, with their settings, for every file of a run."""

    def __init__(self, daw, rate, opts, switches):
        self.engine = daw.RenderEngine(rate, 512)
        self.rate = rate
        self.plugin = opts["--plugin"]
        self.main = load_plugin(self.engine, "plugin", self.plugin)
        print("Plugin: %s (%d in, %d out)" % (os.path.basename(os.path.normpath(self.plugin)), self.main.get_num_input_channels(), self.main.get_num_output_channels()))
        if "--edit" in switches:
            edit(self.main, self.plugin, opts["--state"], "Plugin")
        else:
            print(apply_state(self.main, self.plugin, opts["--state"], "Plugin"))
        self.fx = None
        effect = opts["--effect"]
        if effect:
            self.fx = load_plugin(self.engine, "effect", effect)
            print("Effect: %s" % os.path.basename(os.path.normpath(effect)))
            if "--edit" in switches:
                edit(self.fx, effect, opts["--effect_state"], "Effect")
            else:
                print(apply_state(self.fx, effect, opts["--effect_state"], "Effect"))

    def latency(self):
        n = self.main.get_latency_samples()
        if self.fx is not None:
            n += self.fx.get_latency_samples()
        return max(0, int(n))

    def render(self, np, midi=None, wav=None, tail=2.0):
        """The audio (2 x samples) of the MIDI or the WAV through the chain, its latency taken off; and its length."""
        graph = []
        if wav:
            data, length = read_wav(wav, self.rate)
            graph.append((self.engine.make_playback_processor("input", data), []))
            graph.append((self.main, ["input"]))
        else:
            length = midi_notes(midi)[0]
            if not self.main.load_midi(os.path.abspath(midi), clear_previous=True, beats=False, all_events=True):
                raise ValueError("the plugin could not take %s" % os.path.basename(midi))
            graph.append((self.main, []))
        if self.fx is not None:
            graph.append((self.fx, ["plugin"]))
        if not self.engine.load_graph(graph):
            raise ValueError("the plugins could not be connected")
        late = self.latency()
        self.engine.render(length + tail + late / float(self.rate))
        audio = np.asarray(self.engine.get_audio(), dtype=np.float32)
        if audio.ndim == 1:
            audio = audio[None, :]
        if audio.shape[0] == 1:
            audio = np.vstack([audio, audio])
        # The plugins' own delay (lookahead, oversampling): the hits land where the MIDI has them.
        return audio[:2, late:], length, late


def finish(np, audio, volume):
    """The audio at the volume asked, turned down to fit where it would clip; and a note about it, or None."""
    audio = audio * (volume / 100.0)
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    if peak < 1e-6:
        return audio, "the render is silent. Does the plugin need a kit or patch loaded (--edit), or is it an effect (--wav)?"
    if peak > 1.0:
        return audio / peak * 0.999, "turned down %.1f dB so it does not clip" % (20 * np.log10(peak / 0.999))
    return audio, None


def main(argv):
    opts = {"--plugin": None, "--state": None, "--wav": None, "--effect": None, "--effect_state": None,
            "--rate": "44100", "--tail": "2", "--volume": "100", "--bits": "16", "--dir": None, "--outdir": None,
            "--drummidi": None, "--java": "java"}
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
        if a in ("--edit", "--scan", "--recursive", "--test"):
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
    folder = opts["--dir"]
    if folder:
        if not os.path.isdir(folder):
            fail("no folder %s" % folder)
        if opts["--wav"]:
            fail("--dir plays MIDI files; --wav is for one WAV")
        batch = collect(folder, "--recursive" in switches)
        if not batch:
            fail("no MIDI files in %s" % folder)
    elif not midi and not opts["--wav"]:
        fail("no MIDI file to play (or a WAV with --wav, or a folder with --dir)")
    else:
        batch = None
    test = "--test" in switches
    if test:
        if opts["--wav"]:
            fail("--test checks a MIDI's render; --wav has no MIDI to check against")
        jar = opts["--drummidi"]
        if not jar or not os.path.isfile(jar):
            fail("--test needs DrumMidi_CRT.jar (--drummidi; Pulsekit gives its own)")
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

    chain = Chain(daw, rate, opts, switches)
    late = chain.latency()
    if late:
        print("Latency: %d samples (%.1f ms), taken off the start" % (late, 1000.0 * late / rate))
    if batch is None:
        source = opts["--wav"] or midi
        if not os.path.isfile(source):
            fail("no file at %s" % source)
        if midi and not opts["--wav"]:
            length, notes = midi_notes(midi)
            if not notes:
                fail("%s has no notes" % os.path.basename(midi))
            print("MIDI: %s, %d notes, %.1f s" % (os.path.basename(midi), len(notes), length))
        try:
            audio, length, late = chain.render(np, midi=None if opts["--wav"] else midi, wav=opts["--wav"], tail=tail)
        except ValueError as e:
            fail(str(e))
        if opts["--wav"]:
            print("Input: %s, %.1f s" % (os.path.basename(source), length))
        audio, note = finish(np, audio, volume)
        if note:
            print("Note: " + note)
        if not out:
            out = "%s-%s.wav" % (stem(source), stem(plugin))
        if opts["--outdir"]:
            if not os.path.isdir(opts["--outdir"]):
                os.makedirs(opts["--outdir"])
            out = os.path.join(opts["--outdir"], os.path.basename(out))
        write_wav(out, audio, rate, bits)
        print("Wrote %s: %.1f s, %d Hz, %d-bit stereo" % (out, audio.shape[1] / float(rate), rate, bits))
        batch_done = [(midi, out)]
    else:
        print("vst_render: %d MIDI file%s in %s%s" % (len(batch), "" if len(batch) == 1 else "s", folder, " (and its subfolders)" if "--recursive" in switches else ""))
        batch_done = []
        for m in batch:
            try:
                audio, length, _ = chain.render(np, midi=m, tail=tail)
            except Exception as e:
                print("Failed %s: %s" % (os.path.relpath(m, folder), e))
                continue
            audio, note = finish(np, audio, volume)
            where = opts["--outdir"] or os.path.dirname(m)
            if opts["--outdir"]:
                # Subfolders are kept under --outdir.
                where = os.path.join(opts["--outdir"], os.path.relpath(os.path.dirname(m), folder))
            if not os.path.isdir(where):
                os.makedirs(where)
            o = os.path.join(where, "%s-%s.wav" % (stem(m), stem(plugin)))
            write_wav(o, audio, rate, bits)
            print("Wrote %s: %.1f s%s" % (os.path.relpath(o, opts["--outdir"] or folder), audio.shape[1] / float(rate), " (" + note + ")" if note else ""))
            batch_done.append((m, o))
    if test:
        report = ["vst_render --test: DrumMidi_CRT on each render, its hits against the MIDI played",
                  "Plugin: %s%s" % (os.path.basename(os.path.normpath(plugin)), ", effect " + os.path.basename(os.path.normpath(opts["--effect"])) if opts["--effect"] else ""),
                  "P: DrumMidi's hits that were played, R: played hits DrumMidi found (within %d ms, after the best shift)" % (TOLERANCE * 1000), ""]
        total = {}
        for m, o in batch_done:
            dm = os.path.splitext(o)[0] + "-dm.mid"
            found = drum_midi(opts["--java"], opts["--drummidi"], o, dm)
            name = os.path.basename(o)
            if isinstance(found, str):
                report.append("%s: %s" % (name, found))
                continue
            length, played = midi_notes(m)
            rows, shift = score(played, found, length + 0.05)
            for fam, t, f, ok in rows:
                v = total.setdefault(fam, [0, 0, 0])
                v[0] += t
                v[1] += f
                v[2] += ok
            report.append("%s (shift %+d ms): %s" % (name, round(shift * 1000), score_line(rows)))
        if total:
            report.append("")
            report.append("All: " + score_line([(fam, total[fam][0], total[fam][1], total[fam][2]) for fam, notes in FAMILIES if fam in total]))
        text = "\n".join(report)
        print("")
        print(text)
        where = opts["--outdir"] or (folder if batch is not None else os.path.dirname(os.path.abspath(batch_done[0][1])))
        rep = os.path.join(where, "vst-test-%s.txt" % stem(plugin))
        with open(rep, "w") as f:
            f.write(text + "\n")
        print("Wrote %s" % rep)
    if batch is not None:
        print("Succeeded: %d of %d MIDI files rendered" % (len(batch_done), len(batch)))
    else:
        print("Succeeded: %s" % batch_done[0][1])


if __name__ == "__main__":
    main(sys.argv[1:])
