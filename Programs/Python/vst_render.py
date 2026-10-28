"""
vst_render.py: plays a MIDI file through a VST2 or VST3 instrument and saves what it sounds like as a WAV.

Usage: python vst_render.py [song.mid] [output.wav] [--plugin <plugin>] [--plugin2 <plugin>] [--plugin3 <plugin>] [--carla <carlaproject>] [--drumgizmo <drumkit>] [--midimap <mapfile>] [--state <file>] [--wav <input.wav>] [--effect <plugin>] [--effect_state <file>] [--bpm N] [--bpm_from_name] [--rate N] [--tail N] [--volume N] [--bits N] [--edit] [--dir <folder>] [--dir2 <folder>] [--recursive] [--outdir <folder>] [--skip_existing] [--test] [--no-toms] [--snare-leak] [--map <notes>] [--map2 <notes>] [--map3 <notes>] [--mp3 KBPS] [--keep_wav] [--zip] [--zip_mb N] [--zip_nowav] [--scan]

  song.mid        the MIDI played (every channel; a drum plugin hears the drum channel as it is).
  output.wav      the WAV made; <song>-<plugin>.wav when it is not given.
  --plugin        the instrument: a VST2 (.dll, .so, .vst) or VST3 (.vst3) plugin. With --wav, an
                  effect (an amp, a reverb) that the WAV is played through instead.
  --plugin2, --plugin3
                  more instruments: the whole run (renders, test, MP3s, zip) again with each, after
                  --plugin's: <midi>-<plugin2>.wav and its own report. Each plays with the settings
                  last saved for it with --edit (--state is --plugin's). A plugin that fails is
                  reported and the next one goes on.
  --carla         a Carla project (.carxp, saved in Carla): its plugins play the MIDI, in the Carla-Rack
                  VST (found in the VST folders unless --plugin gives it). Carla hosts LV2, VST2, VST3,
                  SFZ and SoundFonts natively on Linux, and Windows plugins bridged with yabridge, so
                  a Linux computer renders without Windows. Set the kit up in Carla (the plugin's own
                  window), save the project, and give it here; no --edit needed.
  --drumgizmo     a DrumGizmo kit (its drumkit .xml, free kits at drumgizmo.org): DrumGizmo's own
                  program plays the MIDI (Linux; no DawDreamer or plugin needed), its channels
                  (kick, snare, overheads...) mixed to stereo, the ...L and ...R ones to their side.
  --midimap       the kit's MIDI map (.xml): which note plays which drum. Without it, the kit
                  folder's Midimap*.xml is used.
  --state         the plugin's settings (its kit, patch, mixer): a file --edit saved, a .fxp/.fxb
                  (VST2) or a .vstpreset (VST3). Without it, the settings last saved with --edit for
                  this plugin are used (~/.pulsekit/vst/<plugin>.state), else the plugin's defaults.
  --wav           a WAV to play through --plugin (an effect) instead of MIDI.
  --effect        an effect after the instrument (an amp sim, a reverb); --effect_state its settings,
                  as --state (its last --edit settings when not given).
  --bpm N         plays the MIDI at N beats a minute (20 to 400): for a MIDI file with no tempo in it,
                  which plays at 120 otherwise (a DAW may have shown it at its project's tempo), or to
                  hear one faster or slower. The file's own tempo changes are replaced by this one; the
                  file itself is not changed. With --test, its hits are compared at this tempo too.
                  (Pulsekit does not pass its own tempo as --bpm: give it in Params.)
  --bpm_from_name a MIDI file with no tempo of its own (it would play at 120) plays at the tempo its
                  name gives: a number from 40 to 300 in the file's name (127bpmTinytownBeat9,
                  "100 Basic 1 CH", "090 S01 Chorus"), else in its folders' names, nearest first
                  ("200 punk/S10 Outro"). A number with bpm after it counts first; one stuck to
                  letters or in 7-4 does not (S10, F4, 7-4). A file's own tempo is kept (its line
                  says when its name gives another: a half-time groove is named after the song's
                  tempo); one with neither plays at --bpm when given. Each WAV's line says the
                  tempo it was played at.
  --rate N        sample rate: 44100 (default) or 48000.
  --tail N        seconds rendered after the last note, for the sound to ring out (2).
  --volume N      percent, 1 to 400 (100). A mix that would clip is turned down to fit.
  --bits N        16 (default) or 24-bit WAV.
  --edit          opens the plugin's own window first (Windows and Mac): load a kit or patch there,
                  close the window, and its settings are saved (to --state, else
                  ~/.pulsekit/vst/<plugin>.state) and used for this and later runs. With --effect,
                  the effect's window opens after the instrument's.
  --dir           renders every MIDI file in the folder or .pls playlist (the plugin loads once): <midi>-<plugin>.wav
                  beside each, or in --outdir (its subfolders kept). --recursive: subfolders too.
  --dir2          a second folder (fills here, longer patterns there): its MIDI files rendered in the
                  same run. In --outdir each folder's WAVs go under its own name (fills/..., patterns/...).
  --skip_existing with --dir: a MIDI whose WAV is already there (from a run that was stopped) is not
                  rendered again; that WAV is tested and zipped as the new ones are. With --test, a
                  WAV's DrumMidi MIDI (<wav>-dm.mid) newer than the WAV is used as it is, not made again
                  (leave this off after DrumMidi_CRT or --no-toms changed).
  --test          then runs DrumMidi_CRT on each WAV and compares its hits with the MIDI played, per
                  drum family (within 30 ms, after the best shift): a report, vst-test-<plugin>.txt,
                  and DrumMidi's MIDI as <wav>-dm.mid. Pulsekit gives its DrumMidi_CRT.jar (--drummidi)
                  and Java (--java). --no-toms: DrumMidi writes no toms and the report leaves them out.
  --snare-leak    with --test: DrumMidi leaves out the hats a bright snare's wires make in the hat band
                  (its --snare-leak): fewer false hats on the snare, some real ones lost.
  --map notes     with --test: what the plugin really plays for the MIDI's notes, so the report
                  compares DrumMidi with that: 45=46,41=none,57=49 (a note to another, or none: the
                  plugin plays nothing there, or nothing the test should count). Or a text file of
                  such pairs (one or more on a line, # comments). The MIDI files themselves are not
                  changed; the report says which map it used. A file can have a section per plugin:
                  [Anarchy Drums] on a line, then its pairs; the section named in the plugin's file
                  name is used (lines before any section are for every plugin), so one file serves
                  --plugin, --plugin2 and --plugin3.
  --map2, --map3  --plugin2's and --plugin3's own --map (pairs or a file); without one, --map is theirs too.
  --mp3 KBPS      then turns each WAV into an MP3 at KBPS (128, 192, 256, 320...) with Pulsekit's
                  Wav2Mp3 (in its DrumMidi_CRT.jar: --drummidi and --java, which Pulsekit gives) and
                  deletes the WAV: a 20 s WAV of 3.5 MB is about 0.5 MB at 192. With --test the WAVs
                  are tested first. The zip then has the MP3s (mp3/). --keep_wav: the WAVs stay too.
                  With --skip_existing, an MP3 already made counts as the MIDI's render.
  --zip           then puts the MIDI files played (and the copies at --bpm's tempo), the WAVs made, and
                  with --test DrumMidi's MIDI files and the report, into one zip:
                  vst-render-<plugin>-<date>-<time>.zip beside the report (in --outdir when given). The
                  plugin and Carla settings files given go in too, and the switches used (vst-render-run.txt).
                  Without --zip, a --dir or --test run in a terminal asks at the end whether to make it.
  --zip_mb N      zips of at most N MB each (--zip implied): vst-render-...-part1of3.zip and so on, for a
                  chat or mail that takes files up to a size (30 for 30 MB). Each MIDI stays with its WAV
                  and DrumMidi's MIDI, and each zip has the report, the settings and vst-render-run.txt.
  --zip_nowav     the zip without the WAVs (--zip implied): the MIDI files, DrumMidi's MIDI files and the
                  report are small, the WAVs are most of it (a 20 s WAV is about 3.5 MB). A thousand
                  tests fit in one zip this way; send the WAVs of the files that matter by themselves.
                  The MP3s of --mp3 still go in (they are small).
  --scan          lists the plugins in the usual VST folders, then stops.

The plugins' own delay (lookahead, oversampling) is taken off the start, so the hits land where
the MIDI has them.

Carla (free) can be the plugin too: its Carla-Rack or Carla-Patchbay VST hosts other plugins
(also LV2, SFZ and SoundFonts); set it up with --edit, or on Linux with --carla and a project saved
in Carla.

--drumgizmo needs DrumGizmo (Linux: apt install drumgizmo) and numpy; the rest needs DawDreamer and
numpy (pip install dawdreamer), on Windows, Mac or Linux (64-bit Python 3.9+).
A plugin is a desktop program: this does not run on a phone. Licensed plugins render as they do in a
DAW once they are installed and activated on the computer.
"""
import json
import os
import re
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


def lv2_folders():
    """The usual LV2 folders of this computer that exist (their plugins play through --carla)."""
    home = os.path.expanduser("~")
    if sys.platform.startswith("win"):
        dirs = [os.path.join(os.environ.get("CommonProgramFiles", r"C:\Program Files\Common Files"), "LV2"), os.path.join(os.environ.get("APPDATA", home), "LV2")]
    elif sys.platform == "darwin":
        dirs = ["/Library/Audio/Plug-Ins/LV2", os.path.join(home, "Library/Audio/Plug-Ins/LV2")]
    else:
        dirs = [os.path.join(home, ".lv2"), "/usr/lib/lv2", "/usr/local/lib/lv2", "/usr/lib/x86_64-linux-gnu/lv2"]
    return [d for d in dirs if os.path.isdir(d)]


def carla_rack():
    """Carla's Carla-Rack VST in the VST folders (also Carla's own folders); None when there is none."""
    dirs = vst_folders() + [d for d in ("/usr/lib/carla", "/usr/local/lib/carla", r"C:\Program Files\Carla",
                                        "/Applications/Carla.app/Contents/MacOS") if os.path.isdir(d)]
    for d in dirs:
        for root, subdirs, files in os.walk(d):
            for n in sorted(subdirs + files):
                if os.path.splitext(n)[0] == "CarlaRack" and n.lower().endswith(PLUGIN_ENDS):
                    return os.path.join(root, n)
    return None


def carla_state(proc, project):
    """Loads the Carla project's plugins into the Carla-Rack VST; returns the line saying which."""
    import re
    import tempfile
    if not os.path.isfile(project):
        fail("no Carla project at %s" % project)
    text = open(project, "rb").read().decode("utf-8", "replace")
    plugins = re.findall(r"<Plugin>.*?</Plugin>", text, re.S)
    if not plugins:
        fail("%s has no plugins (save the project in Carla with the plugin loaded)" % os.path.basename(project))
    names = [m.strip() for m in re.findall(r"<Name>(.*?)</Name>", "".join(plugins), re.S)]
    work = tempfile.mkdtemp(prefix="pulsekit-carla-")
    empty = os.path.join(work, "empty.state")
    proc.save_state(empty)
    raw = open(empty, "rb").read()
    at = raw.find(b"<?xml")
    end = raw.rfind(b"</CARLA-PROJECT>")
    if at < 4 or end < 0:
        fail("the plugin is not Carla's Carla-Rack (give Carla-Rack with --plugin, or leave --plugin out)")
    xml = raw[at:end] + ("\n".join(" " + p for p in plugins) + "\n</CARLA-PROJECT>\n").encode("utf-8") + b"\0"
    state = os.path.join(work, "project.state")
    # The rack's state is its project text, its length before it.
    with open(state, "wb") as f:
        f.write(raw[:at - 4] + struct.pack(">I", len(xml)) + xml)
    proc.load_state(state)
    # What the rack has now: a plugin Carla could not find is left out.
    check = os.path.join(work, "check.state")
    proc.save_state(check)
    loaded = open(check, "rb").read().count(b"<Plugin>")
    if loaded < len(plugins):
        fail("Carla loaded %d of the %d plugins in %s. Are they installed on this computer (and in Carla's folders)?" % (loaded, len(plugins), os.path.basename(project)))
    return "Carla project: %s (%s)" % (os.path.basename(project), ", ".join(names))


def gizmo_map(kit, given):
    """The kit's MIDI map: the one given, else the kit folder's Midimap*.xml (a full one first)."""
    if given:
        if not os.path.isfile(given):
            fail("no MIDI map at %s" % given)
        return given
    folder = os.path.dirname(os.path.abspath(kit))
    maps = sorted(n for n in os.listdir(folder) if n.lower().startswith("midimap") and n.lower().endswith(".xml"))
    if not maps:
        fail("no Midimap*.xml in %s: give the kit's MIDI map with --midimap" % folder)
    full = [n for n in maps if "full" in n.lower()]
    return os.path.join(folder, (full or maps)[0])


def gizmo_side(name):
    """Where a kit channel goes in the stereo mix: -1 left (OHL, AmbL, Room_Left), 1 right, 0 the middle."""
    low = name.lower()
    if low.endswith("left") or low.endswith(("_l", "-l", " l")) or name.endswith("L"):
        return -1
    if low.endswith("right") or low.endswith(("_r", "-r", " r")) or name.endswith("R"):
        return 1
    return 0


def wav_samples(path):
    """A mono WAV DrumGizmo wrote, as floats: 32-bit float read from the disk as it is needed (it can be long)."""
    import numpy as np
    with open(path, "rb") as f:
        data = f.read(4096)
    p = 12
    fmt, bits = 3, 32
    while p + 8 <= len(data):
        cid = data[p:p + 4]
        size = struct.unpack("<I", data[p + 4:p + 8])[0]
        if cid == b"fmt ":
            fmt, _, _, _, _, bits = struct.unpack("<HHIIHH", data[p + 8:p + 24])
        elif cid == b"data":
            n = (os.path.getsize(path) - p - 8) // (bits // 8)
            if n <= 0:
                return np.zeros(0, dtype=np.float32)
            if fmt == 3 and bits == 32:
                return np.memmap(path, dtype="<f4", mode="r", offset=p + 8, shape=(n,))
            if bits == 16:
                return np.memmap(path, dtype="<i2", mode="r", offset=p + 8, shape=(n,)).astype(np.float32) / 32768.0
            break
        p += 8 + size + (size & 1)
    fail("%s: a WAV format DrumGizmo does not write" % os.path.basename(path))


def midi_file(path, notes, bpm=120, tpq=480):
    """A drum MIDI of (beat, note) hits, for the DrumGizmo check."""
    def vlq(v):
        out = [v & 0x7f]
        v >>= 7
        while v:
            out.insert(0, (v & 0x7f) | 0x80)
            v >>= 7
        return bytes(out)
    us = int(60000000 / bpm)
    ev = b"\x00\xff\x51\x03" + bytes([(us >> 16) & 0xff, (us >> 8) & 0xff, us & 0xff])
    at = 0
    for beat, note in notes:
        tick = int(beat * tpq)
        ev += vlq(tick - at) + bytes([0x99, note, 110]) + vlq(10) + bytes([0x89, note, 0])
        at = tick + 10
    ev += b"\x00\xff\x2f\x00"
    with open(path, "wb") as f:
        f.write(b"MThd" + struct.pack(">IHHH", 6, 0, 1, tpq) + b"MTrk" + struct.pack(">I", len(ev)) + ev)


class Gizmo:
    """
    DrumGizmo's own program on a kit: each MIDI rendered, its channels mixed to stereo.

    Its MIDI file player puts each hit at the start of a block of about a thousand samples (up to
    23 ms early at 44.1 kHz), and some versions play the file at half speed. So the kit is rendered
    at four or eight times the rate (the blocks a few ms long) and every 4th or 8th sample kept; the
    speed is measured first, with one hit four seconds in.
    """

    def __init__(self, rate, opts):
        import shutil
        self.rate = rate
        self.kit = opts["--drumgizmo"]
        if not os.path.isfile(self.kit):
            fail("no DrumGizmo kit at %s (give its drumkit .xml)" % self.kit)
        self.exe = shutil.which("drumgizmo")
        if not self.exe:
            fail("DrumGizmo is not installed (Linux: sudo apt install drumgizmo). On Windows or Mac, give its VST with --plugin")
        self.midimap = gizmo_map(self.kit, opts["--midimap"])
        import re
        notes = [int(n) for n in re.findall(r'note\s*=\s*"(\d+)"', open(self.midimap, "rb").read().decode("utf-8", "replace"))]
        if not notes:
            fail("%s maps no notes" % os.path.basename(self.midimap))
        self.speed = 1.0
        self.speed = self.measure(notes[0])
        print("DrumGizmo: %s, MIDI map %s%s" % (self.kit, os.path.basename(self.midimap), "" if abs(self.speed - 1) < 0.01 else ", its MIDI speed %.3g corrected" % self.speed))

    def latency(self):
        return 0

    def run(self, midi, seconds, factor):
        """DrumGizmo on the MIDI at `factor` times the rate: its folder and channel files (name, path)."""
        import subprocess
        import tempfile
        work = tempfile.mkdtemp(prefix="pulsekit-gizmo-")
        cmd = [self.exe, "-i", "midifile", "-I", "file=%s,midimap=%s,speed=%.6f" % (os.path.abspath(midi), os.path.abspath(self.midimap), self.speed),
               "-o", "wavfile", "-O", "file=%s,srate=%d" % (os.path.join(work, "out"), self.rate * factor),
               "-e", str(int(seconds * self.rate * factor)), os.path.abspath(self.kit)]
        done = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        made = sorted(n for n in os.listdir(work) if n.startswith("out") and n.endswith(".wav"))
        if done.returncode != 0 or not made:
            last = done.stdout.decode("utf-8", "replace").strip().splitlines()[-3:]
            raise ValueError("DrumGizmo could not render it (%s)" % " / ".join(last))
        # outKick-0.wav: the channel's name, then its number.
        return work, [(n[3:-4].rsplit("-", 1)[0], os.path.join(work, n)) for n in made]

    @staticmethod
    def clean(work, files):
        for _, path in files:
            os.remove(path)
        os.rmdir(work)

    def measure(self, note):
        """DrumGizmo's MIDI speed: where a hit four seconds in (at 120 BPM) lands, against where it should."""
        import numpy as np
        import tempfile
        midi = os.path.join(tempfile.mkdtemp(prefix="pulsekit-gizmo-check-"), "check.mid")
        midi_file(midi, [(8, note)])
        try:
            work, files = self.run(midi, 9.5, 4)
        except ValueError as e:
            fail(str(e))
        first = None
        for _, path in files:
            audio = np.abs(np.asarray(wav_samples(path)))
            loud = np.nonzero(audio > max(1e-4, float(audio.max()) * 0.05))[0] if audio.size else []
            if len(loud) and (first is None or loud[0] < first):
                first = int(loud[0])
        self.clean(work, files)
        os.remove(midi)
        os.rmdir(os.path.dirname(midi))
        if first is None:
            fail("DrumGizmo played nothing for note %d of %s: does the MIDI map fit the kit?" % (note, os.path.basename(self.midimap)))
        landed = first / float(self.rate * 4)
        return round(landed / 4.0, 3) if landed > 1 else 1.0

    def render(self, np, midi=None, wav=None, tail=2.0):
        if wav:
            raise ValueError("DrumGizmo plays MIDI; --wav needs an effect plugin")
        length = midi_notes(midi)[0]
        frames = int((length + tail) * self.rate)
        # Eight times the rate for a short MIDI (hits within 2 ms), four for a long one (5 ms): less disk.
        factor = 8 if length + tail <= 60 else 4
        work, files = self.run(midi, length + tail, factor)
        mix = np.zeros((2, frames), dtype=np.float32)
        for channel, path in files:
            audio = np.asarray(wav_samples(path)[::factor][:frames], dtype=np.float32)
            side = gizmo_side(channel)
            left, right = (1.0, 0.0) if side < 0 else (0.0, 1.0) if side > 0 else (0.7071, 0.7071)
            mix[0, :len(audio)] += audio * left
            mix[1, :len(audio)] += audio * right
        self.clean(work, files)
        return mix, length, 0


def scan():
    rack = carla_rack()
    print("Carla-Rack (for --carla): %s" % (rack or "not found; Carla is free at kx.studio/carla"))
    import shutil
    print("DrumGizmo (for --drumgizmo): %s" % (shutil.which("drumgizmo") or "not installed (Linux: apt install drumgizmo)"))
    for d in lv2_folders():
        bundles = sorted(n for n in os.listdir(d) if n.lower().endswith(".lv2"))
        print("%s (LV2, through --carla): %s" % (d, ", ".join(b[:-4] for b in bundles) or "none"))
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


def retempo(path, bpm, folder):
    """A copy of the MIDI file at one tempo, bpm (its own tempo changes left out), in folder; its path."""
    d = open(path, "rb").read()
    if d[:4] != b"MThd":
        raise ValueError("%s is not a MIDI file" % os.path.basename(path))
    head = 8 + struct.unpack(">I", d[4:8])[0]
    out = bytearray(d[:head])
    us = int(round(60000000.0 / bpm))
    tempo = b"\x00\xff\x51\x03" + bytes([(us >> 16) & 0xFF, (us >> 8) & 0xFF, us & 0xFF])
    i, first = head, True
    while i + 8 <= len(d):
        kind, size = d[i:i + 4], struct.unpack(">I", d[i + 4:i + 8])[0]
        p, end = i + 8, min(len(d), i + 8 + size)
        if kind != b"MTrk":
            out += d[i:end]
            i += 8 + size
            continue
        body = bytearray(tempo if first else b"")
        first = False
        carry, status = 0, 0
        while p < end:
            delta, q = var_len(d, p)
            delta += carry
            b = d[q]
            if b == 0xFF:
                n, r = var_len(d, q + 2)
                event, after = d[q:r + n], r + n
                if d[q + 1] == 0x51:
                    # A tempo change: left out, its time kept for the next event.
                    carry, p = delta, after
                    continue
            elif b in (0xF0, 0xF7):
                n, r = var_len(d, q + 1)
                event, after = d[q:r + n], r + n
            else:
                if b & 0x80:
                    status = b
                    r = q + 1
                else:
                    # Running status: written out in full, as the event before it may be gone.
                    r = q
                hi = status & 0xF0
                after = r + (1 if hi in (0xC0, 0xD0) else 2)
                event = bytes([status]) + d[r:after]
            carry = 0
            body += write_var(delta) + event
            p = after
        out += b"MTrk" + struct.pack(">I", len(body)) + body
        i += 8 + size
    to = os.path.join(folder, os.path.basename(path))
    with open(to, "wb") as f:
        f.write(out)
    return to


def write_var(v):
    """A MIDI variable-length number."""
    b = [v & 0x7F]
    v >>= 7
    while v:
        b.insert(0, (v & 0x7F) | 0x80)
        v >>= 7
    return bytes(b)


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
TOMS = (41, 43, 45, 47, 48, 50)


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


MP3_RATES = (32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)


def to_mp3(java, jar, wav, mp3, kbps):
    """Wav2Mp3 (in DrumMidi_CRT.jar) on the WAV; None when the MP3 was made, else why not."""
    import subprocess
    try:
        r = subprocess.run([java, "-cp", os.path.abspath(jar), "Wav2Mp3", os.path.abspath(wav), os.path.abspath(mp3), "--bitrate", str(kbps)],
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=1800)
    except Exception as e:
        return "Wav2Mp3 did not run (%s)" % e
    said = r.stdout.decode("utf-8", "replace").strip().splitlines()
    failed = [l for l in said if l.startswith("Failed") or "Exception" in l or "Error" in l]
    if r.returncode != 0 or failed or not os.path.isfile(mp3) or os.path.getsize(mp3) == 0:
        return (failed or said[-1:] or ["no MP3 written"])[0]
    return None


def file_tempo(path):
    """The MIDI file's own (first) tempo in BPM, or None when it has none (it plays at 120)."""
    try:
        with open(path, "rb") as f:
            data = f.read()
    except OSError:
        return None
    i = data.find(b"\xff\x51\x03")
    if i < 0 or i + 6 > len(data):
        return None
    us = int.from_bytes(data[i + 3:i + 6], "big")
    return round(60000000.0 / us, 2) if us else None


def name_tempo(path, root=None):
    """--bpm_from_name: a tempo (40-300) in the MIDI's name, else in its folders' names (nearest first,
    up to `root`): a number with bpm after it first, else one standing alone (not S10, F4 or 7-4)."""
    names = [os.path.splitext(os.path.basename(path))[0]]
    d = os.path.dirname(os.path.abspath(path))
    top = os.path.abspath(root) if root else None
    while d and len(names) < 6:
        names.append(os.path.basename(d))
        if top and os.path.normcase(d) == os.path.normcase(top):
            break
        parent = os.path.dirname(d)
        if parent == d:
            break
        d = parent
    for n in names:
        for m in re.finditer(r"(\d{2,3})\s*bpm", n, re.I):
            v = int(m.group(1))
            if 40 <= v <= 300:
                return v
        for m in re.finditer(r"(?<![A-Za-z0-9.\-])(\d{2,3})(?![A-Za-z0-9.\-])", n):
            v = int(m.group(1))
            if 40 <= v <= 300:
                return v
    return None


def note_map(text, plugin=None):
    """--map: {note: note or None} from "45=46,41=none" or a text file of such pairs (# comments)."""
    return map_for(text, plugin)[0]


def map_key(name):
    """A plugin's or a map section's name to match on: letters and digits, lower case."""
    return re.sub(r"[^a-z0-9]", "", name.lower())


def map_for(text, plugin=None):
    """--map's notes for the plugin, and which part of the map they came from.

    A text file can have a section per plugin ([Anarchy Drums] on a line, then its pairs): the
    section whose name is in the plugin's file name (or the other way round; case, spaces and
    signs aside) is used, over the lines before any section (for every plugin)."""
    section = None
    if os.path.isfile(text):
        common, parts, at = [], {}, None
        with open(text) as f:
            for line in f:
                line = line.split("#", 1)[0].strip()
                head = re.match(r"^\[(.+)\]$", line)
                if head:
                    at = head.group(1).strip()
                    parts.setdefault(at, [])
                elif line:
                    (parts[at] if at is not None else common).append(line)
        want = map_key(os.path.splitext(os.path.basename(os.path.normpath(plugin)))[0]) if plugin else ""
        pick = None
        for name in parts:
            k = map_key(name)
            if want and k and (k in want or want in k):
                pick = name
                break
        text = "\n".join(common + (parts[pick] if pick else []))
        if parts:
            section = pick or ""
    out = {}
    for pair in re.split(r"[,;\s]+", text.strip()):
        if not pair:
            continue
        if "=" not in pair:
            raise ValueError("%s is not note=note (45=46, or 41=none)" % pair)
        a, b = (x.strip() for x in pair.split("=", 1))
        try:
            src = int(a)
            dst = None if b.lower() in ("none", "-", "off", "") else int(b)
        except ValueError:
            raise ValueError("%s is not note=note (45=46, or 41=none)" % pair)
        if not 0 <= src <= 127 or dst is not None and not 0 <= dst <= 127:
            raise ValueError("%s: MIDI notes are 0 to 127" % pair)
        out[src] = dst
    return out, section


def plugin_runs(argv, opts):
    """--plugin2/--plugin3: each plugin's own run, (plugin, its arguments): --plugin swapped for it,
    --state only for the first, and --map2/--map3 as that plugin's --map (else --map for all)."""
    plugins = [(opts["--plugin"], None)] + [(opts[k], opts[m]) for k, m in (("--plugin2", "--map2"), ("--plugin3", "--map3")) if opts[k]]
    runs = []
    for k, (plug, own_map) in enumerate(plugins):
        one = []
        j = 0
        while j < len(argv):
            a = argv[j]
            if a in ("--plugin2", "--plugin3", "--map2", "--map3") or (a == "--state" and k > 0) or (a == "--map" and own_map):
                j += 2
                continue
            if a == "--plugin":
                one += [a, plug]
                j += 2
                continue
            one.append(a)
            j += 1
        if own_map:
            one += ["--map", own_map]
        runs.append((plug, one))
    return runs


def mapped(notes, nmap):
    """The played notes as the plugin plays them (--map): moved to another note, or left out."""
    if not nmap:
        return notes
    return [(t, nmap.get(n, n)) for t, n in notes if nmap.get(n, n) is not None]


def whole_wav(path):
    """A WAV written to its end: sound in it, all of its frames in the file (not one cut off by a stop)."""
    try:
        if not os.path.isfile(path):
            return False
        w = wave.open(path, "rb")
        try:
            need = w.getnframes() * w.getnchannels() * w.getsampwidth()
        finally:
            w.close()
        return need > 0 and os.path.getsize(path) >= need + 44
    except Exception:
        return False


def drum_midi(java, jar, wav, mid, extra=()):
    """DrumMidi_CRT on the WAV (with `extra` switches); returns its notes, or an error text."""
    import subprocess
    try:
        # DrumMidi_CRT writes into its working folder: run it in the WAV's.
        where = os.path.dirname(os.path.abspath(mid))
        r = subprocess.run([java, "-cp", os.path.abspath(jar), "DrumMidi_CRT", os.path.abspath(wav), os.path.basename(mid)] + list(extra),
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


def pls_midis(path):
    """A playlist's (.pls) MIDI files, in its order; a name without a folder is beside it."""
    found = []
    beside = os.path.dirname(os.path.abspath(path))
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            t = line.strip()
            m = re.match(r"(?i)file\d+=(.+)", t)
            if not m:
                continue
            name = m.group(1).strip()
            full = name if os.path.isabs(name) else os.path.join(beside, name)
            if not os.path.isfile(full):
                print("Not found: %s" % name)
            elif name.lower().endswith((".mid", ".midi")) and not name.lower().endswith("-dm.mid"):
                found.append(full)
    return found


class Chain(object):
    """The plugin (and the effect) loaded once, with their settings, for every file of a run."""

    def __init__(self, daw, rate, opts, switches):
        self.engine = daw.RenderEngine(rate, 512)
        self.rate = rate
        self.plugin = opts["--plugin"]
        self.main = load_plugin(self.engine, "plugin", self.plugin)
        print("Plugin: %s (%d in, %d out)" % (os.path.basename(os.path.normpath(self.plugin)), self.main.get_num_input_channels(), self.main.get_num_output_channels()))
        if opts["--carla"]:
            print(carla_state(self.main, opts["--carla"]))
        elif "--edit" in switches:
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
        if audio.shape[0] > 2:
            # A drum plugin with an output for each drum (DrumGizmo's VST): the others go in the middle.
            rest = audio[2:].sum(axis=0) * 0.7071
            audio = np.vstack([audio[0] + rest, audio[1] + rest])
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


# Stop asked: Pulsekit's Stop script (the PULSEKIT_STOP_FILE it makes) or Ctrl+C in a terminal. The step
# in progress (rendering, the test, the zips) ends after its file, and the run goes on to the next step
# with what was done; Ctrl+C again ends it at once.
STOP = {"asked": False}


def ctrl_c(sig, frame):
    if STOP["asked"]:
        raise KeyboardInterrupt
    STOP["asked"] = True
    print("\nStopping after the file in progress (Ctrl+C again ends it now)")


def stop_asked():
    """True once when a stop was asked (the ask is used up: the next step runs until asked again)."""
    f = os.environ.get("PULSEKIT_STOP_FILE")
    if f and os.path.exists(f):
        try:
            os.remove(f)
        except OSError:
            pass
        return True
    if STOP["asked"]:
        STOP["asked"] = False
        return True
    return False


def stoppable(items, step, after):
    """The items one by one, until a stop is asked: then a line saying how far the step got."""
    done = 0
    for item in items:
        if stop_asked():
            print("Stopped %s: %d of %d done; %s" % (step, done, len(items), after))
            return
        yield item
        done += 1


def ask_zip():
    """A --dir or --test run in a terminal: asks whether to zip what it used and made (no: Enter), and
    the largest zip in MB (Enter: one zip). None for no zip, else the MB limit (0: one zip)."""
    try:
        if not sys.stdin or not sys.stdin.isatty():
            return None
        if input("Put the MIDI files, the WAVs and the report into a zip? [y/N] ").strip().lower() not in ("y", "yes"):
            return None
        size = input("Largest zip in MB, for several zips (Enter: one zip) ").strip()
        try:
            return max(0.0, float(size)) if size else 0.0
        except ValueError:
            print("Not a number: one zip")
            return 0.0
    except (EOFError, OSError, ValueError):
        return None


ZIP_MB = 1000 * 1000


def packed_size(path):
    """How many bytes the file takes in a zip (deflated as zipfile does it)."""
    import zlib
    z = zlib.compressobj(zlib.Z_DEFAULT_COMPRESSION, zlib.DEFLATED, -15)
    n = 0
    with open(path, "rb") as f:
        while True:
            b = f.read(1 << 20)
            if not b:
                break
            n += len(z.compress(b))
    return min(n + len(z.flush()), os.path.getsize(path) + 64)


def make_zip(where, plugin, argv, folder, played, wavs, dms, report, opts, bpm, limit_mb=0, no_wav=False, relfn=None):
    """--zip: the inputs and what was made in one zip beside the report (or, with --zip_mb, in zips of at
    most that many MB); their paths.

    midi/ the MIDI files played (their subfolders under --dir kept), midi-<bpm>bpm/ the copies played at
    --bpm's tempo, wav/ the renders, dm/ DrumMidi's MIDI files (--test), settings/ the settings files
    given, and at the top the report and vst-render-run.txt (the switches used). Split, each zip keeps a
    MIDI with its WAV and DrumMidi's MIDI, and has the report, the settings and the run in it too."""
    import time
    import zipfile
    if not os.path.isdir(where):
        os.makedirs(where)
    base = "vst-render-%s-%s" % (re.sub(r"[^A-Za-z0-9._-]+", "_", plugin), time.strftime("%Y%m%d-%H%M%S"))
    used = set()

    def arc(sub, f, root=None):
        if root and os.path.abspath(f).startswith(os.path.abspath(root) + os.sep):
            rel = os.path.relpath(f, root)
        else:
            # A path already made relative (two folders: under each one's name) stays as it is.
            rel = f if not os.path.isabs(f) and os.sep in f else os.path.basename(f)
        a = (sub + "/" if sub else "") + rel.replace(os.sep, "/")
        head, ext = os.path.splitext(a)
        k = 2
        while a in used:
            a = "%s-%d%s" % (head, k, ext)
            k += 1
        used.add(a)
        return a
    out_root = opts["--outdir"] or folder
    # Each MIDI's files together (a unit), then what every zip has (the shared ones).
    dm_left = list(dms)
    units = []
    for k, (m, copy) in enumerate(played):
        unit = []
        if m and os.path.isfile(m):
            unit.append((m, arc("midi" if m.lower().endswith((".mid", ".midi")) else "input", relfn(m) if relfn else m, None if relfn else folder)))
        if copy and os.path.isfile(copy):
            unit.append((copy, arc(("midi-%gbpm" % bpm) if bpm else "midi-at-tempo", relfn(m) if relfn else m, None if relfn else folder)))
        o = wavs[k] if k < len(wavs) else None
        if o and os.path.isfile(o):
            if not (no_wav and o.lower().endswith(".wav")):
                unit.append((o, arc("mp3" if o.lower().endswith(".mp3") else "wav", o, out_root)))
            dm = os.path.splitext(o)[0] + "-dm.mid"
            if dm in dm_left:
                dm_left.remove(dm)
                unit.append((dm, arc("dm", dm, out_root)))
        if unit:
            units.append(unit)
    for d in dm_left:
        units.append([(d, arc("dm", d, out_root))])
    shared = []
    if opts["--dir"] and opts["--dir"].lower().endswith(".pls") and os.path.isfile(opts["--dir"]):
        shared.append((opts["--dir"], arc("", opts["--dir"])))
    for key in ("--state", "--effect_state", "--carla", "--midimap"):
        f = opts[key]
        if f and os.path.isfile(f):
            shared.append((f, arc("settings", f)))
    if report and os.path.isfile(report):
        shared.append((report, arc("", report)))
    run = "vst_render.py " + " ".join('"%s"' % a if " " in a else a for a in argv) + "\n"
    run_name = arc("", "vst-render-run.txt")

    # The zips: units in order, a new zip when the next unit would not fit (a unit bigger than the
    # limit gets a zip of its own).
    def cost(files):
        return sum(packed_size(f) + 120 + 2 * len(a.encode("utf-8")) for f, a in files)
    groups = [[]]
    if limit_mb and limit_mb > 0 and len(units) > 50:
        print("Zipping: measuring %d MIDI files' files for %g MB zips" % (len(units), limit_mb))
    if limit_mb and limit_mb > 0:
        limit = int(limit_mb * ZIP_MB)
        room = limit - cost(shared) - len(run) - 200 - 22
        filled = 0
        for unit in units:
            c = cost(unit)
            if groups[-1] and filled + c > room:
                groups.append([])
                filled = 0
            if c > room:
                print("Note: %s alone is bigger than %g MB: its zip is bigger" % (os.path.basename(unit[0][0]), limit_mb))
            groups[-1].append(unit)
            filled += c
    else:
        groups = [units]
    paths = []
    count = 0
    total_units = sum(len(g) for g in groups)
    zipped = 0
    stopped = False
    for g, group in enumerate(groups):
        if stopped:
            break
        name = base if len(groups) == 1 else "%s-part%dof%d" % (base, g + 1, len(groups))
        path = os.path.join(where, name + ".zip")
        n = 2
        while os.path.exists(path):
            path = os.path.join(where, "%s-%d.zip" % (name, n))
            n += 1
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
            for unit in group:
                if stop_asked():
                    print("Stopped zipping: %d of %d MIDI files' files zipped" % (zipped, total_units))
                    stopped = True
                    break
                for f, a in unit:
                    z.write(f, a)
                    count += 1
                zipped += 1
                if zipped % 50 == 0:
                    print("Zipping: %d of %d" % (zipped, total_units))
            for f, a in shared:
                z.write(f, a)
            z.writestr(run_name, run)
        paths.append(path)
    count += len(shared)
    if len(paths) == 1:
        print("Zipped %d file%s" % (count, "" if count == 1 else "s"))
    else:
        print("Zipped %d file%s in %d zips of at most %g MB (each has the report, the settings and the run)" % (count, "" if count == 1 else "s", len(paths), limit_mb))
    return paths


def main(argv):
    try:
        import signal
        signal.signal(signal.SIGINT, ctrl_c)
    except (ImportError, ValueError):
        pass
    opts = {"--plugin": None, "--carla": None, "--drumgizmo": None, "--midimap": None, "--state": None, "--wav": None, "--effect": None, "--effect_state": None,
            "--bpm": None, "--rate": "44100", "--tail": "2", "--volume": "100", "--bits": "16", "--dir": None, "--outdir": None,
            "--drummidi": None, "--java": "java", "--zip_mb": None, "--mp3": None, "--map": None,
            "--plugin2": None, "--plugin3": None, "--dir2": None, "--map2": None, "--map3": None}
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
        if a in ("--edit", "--scan", "--recursive", "--test", "--no-toms", "--snare-leak", "--zip", "--zip_nowav", "--skip_existing", "--keep_wav", "--bpm_from_name"):
            switches.add(a)
        elif a.startswith("--"):
            fail("unknown switch %s" % a)
        else:
            files.append(a)
        i += 1
    if "--scan" in switches:
        scan()
        return
    for k, m in (("--plugin2", "--map2"), ("--plugin3", "--map3")):
        if opts[m] and not opts[k]:
            fail("%s is the map for %s: give %s too" % (m, k, k))
    more = [opts[k] for k in ("--plugin2", "--plugin3") if opts[k]]
    if more:
        if not opts["--plugin"]:
            fail("--plugin2 and --plugin3 add plugins to --plugin: give --plugin too")
        if opts["--carla"] or opts["--drumgizmo"]:
            fail("--plugin2 and --plugin3 are VST plugins played after --plugin; not with --carla or --drumgizmo")
        if "--edit" in switches:
            fail("--edit opens one plugin's window: save each plugin's settings with its own run first")
        runs = plugin_runs(argv, opts)
        done = []
        for k, (plug, one) in enumerate(runs):
            print("== Plugin %d of %d: %s" % (k + 1, len(runs), os.path.basename(os.path.normpath(plug))))
            try:
                main(one)
                done.append((plug, "done"))
            except SystemExit as e:
                done.append((plug, "failed" if e.code else "done"))
            print("")
        print("Plugins: " + ", ".join("%s %s" % (os.path.basename(os.path.normpath(p)), r) for p, r in done))
        if all(r == "failed" for p, r in done):
            sys.exit(1)
        return
    midi = next((f for f in files if f.lower().endswith((".mid", ".midi"))), None)
    out = next((f for f in files if f.lower().endswith((".wav", ".wave"))), None)
    if opts["--carla"]:
        if "--edit" in switches:
            fail("--carla plays a project set up in Carla: change the kit there and save the project again")
        if not opts["--plugin"]:
            opts["--plugin"] = carla_rack()
            if not opts["--plugin"]:
                fail("--carla needs Carla's Carla-Rack VST (install Carla, or give it with --plugin)")
    gizmo = opts["--drumgizmo"]
    if gizmo:
        if opts["--plugin"] or opts["--carla"] or opts["--effect"] or opts["--wav"] or "--edit" in switches:
            fail("--drumgizmo plays the kit with DrumGizmo's own program: no --plugin, --carla, --effect, --wav or --edit with it")
        opts["--plugin"] = gizmo
    plugin = opts["--plugin"]
    if not plugin:
        fail("no plugin: give one with --plugin (--scan lists the installed ones)")
    # The WAVs and the report are named after the Carla project or the kit, not the rack.
    name = opts["--carla"] or plugin
    if gizmo and stem(gizmo).lower() == "drumkit":
        name = os.path.basename(os.path.dirname(os.path.abspath(gizmo))) or gizmo
    folder = opts["--dir"]
    # A playlist (.pls) for the folder: its MIDI files (PlayList --midi makes one).
    pls = bool(folder) and folder.lower().endswith(".pls") and os.path.isfile(folder)
    if folder:
        if not os.path.isdir(folder) and not pls:
            fail("no folder %s" % folder)
        if opts["--wav"]:
            fail("--dir plays MIDI files; --wav is for one WAV")
        batch = pls_midis(folder) if pls else collect(folder, "--recursive" in switches)
        if pls:
            folder = os.path.dirname(os.path.abspath(folder))
        roots = {m: folder for m in batch}
        if opts["--dir2"]:
            second = opts["--dir2"]
            if not os.path.isdir(second):
                fail("no folder %s (--dir2)" % second)
            more = collect(second, "--recursive" in switches)
            if not more:
                fail("no MIDI files in %s (--dir2)" % second)
            for m in more:
                roots.setdefault(m, second)
            batch = batch + [m for m in more if roots[m] == second]
        if not batch:
            fail("no MIDI files in %s" % folder)
    elif not midi and not opts["--wav"]:
        fail("no MIDI file to play (or a WAV with --wav, or a folder with --dir)")
    else:
        batch = None
        roots = {}
    if opts["--dir2"] and not opts["--dir"]:
        fail("--dir2 is a second folder to --dir: give --dir too")
    # With two folders, each one's files go under its own name in --outdir.
    two = bool(opts["--dir2"])
    names = {}
    for r in sorted(set(roots.values()), key=lambda r: r != folder):
        n = os.path.basename(os.path.normpath(r)) or "folder"
        names[r] = n if n not in names.values() else "%s-%d" % (n, len(names) + 1)

    def rel(m):
        """The MIDI's path under its folder (with two folders, under the folder's name)."""
        r = roots.get(m, folder)
        sub = os.path.relpath(m, r) if r else os.path.basename(m)
        return os.path.join(names[r], sub) if two and r in names else sub

    def shown(path):
        """A path as the log shows it: under --outdir, else under its folder."""
        if opts["--outdir"]:
            return os.path.relpath(path, opts["--outdir"])
        for r in roots.values():
            if os.path.abspath(path).startswith(os.path.abspath(r) + os.sep):
                return (names[r] + os.sep if two else "") + os.path.relpath(path, r)
        return path
    test = "--test" in switches
    if test:
        if opts["--wav"]:
            fail("--test checks a MIDI's render; --wav has no MIDI to check against")
        jar = opts["--drummidi"]
        if not jar or not os.path.isfile(jar):
            fail("--test needs DrumMidi_CRT.jar (--drummidi; Pulsekit gives its own)")
    nmap = {}
    map_part = None
    if opts["--map"]:
        if not test:
            fail("--map is for --test: what the plugin plays for the MIDI's notes")
        try:
            nmap, map_part = map_for(opts["--map"], name)
        except (ValueError, OSError) as e:
            fail("--map: %s" % e)
    mp3_kbps = None
    if opts["--mp3"]:
        try:
            mp3_kbps = int(opts["--mp3"])
        except ValueError:
            fail("--mp3 takes kbps (128, 192, 320...)")
        if mp3_kbps not in MP3_RATES:
            fail("--mp3 is one of %s kbps" % ", ".join(str(r) for r in MP3_RATES))
        if not opts["--drummidi"] or not os.path.isfile(opts["--drummidi"]):
            fail("--mp3 needs Pulsekit's DrumMidi_CRT.jar, which has Wav2Mp3 (--drummidi; Pulsekit gives its own)")
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
    zip_mb = 0.0
    if opts["--zip_mb"]:
        try:
            zip_mb = float(opts["--zip_mb"])
        except ValueError:
            fail("--zip_mb takes a number (MB)")
        if zip_mb < 1:
            fail("--zip_mb is 1 or more (MB)")
    bpm = None
    if opts["--bpm"]:
        try:
            bpm = float(opts["--bpm"])
        except ValueError:
            fail("--bpm takes a number (beats a minute)")
        if not 20 <= bpm <= 400:
            fail("--bpm is 20 to 400")
        if opts["--wav"]:
            fail("--bpm is a MIDI's tempo; --wav plays a WAV as it is")
    by_name = "--bpm_from_name" in switches
    if by_name and opts["--wav"]:
        fail("--bpm_from_name is a MIDI's tempo; --wav plays a WAV as it is")
    # --bpm (and --bpm_from_name): each MIDI is played from a copy at that tempo (the file itself stays as it is).
    import tempfile
    tempo_dir = tempfile.mkdtemp(prefix="vst-bpm-") if bpm or by_name else None
    played_as = {}
    tempo_of = {}

    def tempo(m):
        """The tempo the MIDI is played at: --bpm; with --bpm_from_name its own if it has one, else its
        name's, else --bpm; None: its own."""
        if m not in tempo_of:
            if not by_name:
                tempo_of[m] = bpm
            else:
                own = file_tempo(m)
                tempo_of[m] = None if own else (name_tempo(m, roots.get(m)) or bpm)
        return tempo_of[m]

    def at_tempo(m):
        b = tempo(m)
        if not b:
            return m
        if m not in played_as:
            played_as[m] = retempo(m, b, tempfile.mkdtemp(dir=tempo_dir))
        return played_as[m]

    def tempo_note(m):
        b = tempo(m)
        if not b:
            if not by_name:
                return ""
            own, named = file_tempo(m), name_tempo(m, roots.get(m))
            if own and named and abs(own - named) > 1:
                return " at its own %g BPM (its name says %d)" % (own, named)
            return " at its own %g BPM" % own if own else ""
        return " at %g BPM (%s)" % (b, "name" if by_name and name_tempo(m, roots.get(m)) else "--bpm")
    if by_name:
        print("Tempo: each MIDI's own; one with none takes its name's or its folder's%s" % (", else %g BPM (--bpm)" % bpm if bpm else ""))
    if bpm:
        print("Tempo: %g BPM (--bpm)" % bpm)
    try:
        import numpy as np
    except ImportError as e:
        fail("numpy is not installed (%s). Install it with: pip install numpy" % e, 2)
    if gizmo:
        chain = Gizmo(rate, opts)
    else:
        try:
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
            length, notes = midi_notes(at_tempo(midi))
            if not notes:
                fail("%s has no notes" % os.path.basename(midi))
            print("MIDI: %s, %d notes, %.1f s" % (os.path.basename(midi), len(notes), length))
        try:
            audio, length, late = chain.render(np, midi=None if opts["--wav"] else at_tempo(midi), wav=opts["--wav"], tail=tail)
        except ValueError as e:
            fail(str(e))
        if opts["--wav"]:
            print("Input: %s, %.1f s" % (os.path.basename(source), length))
        audio, note = finish(np, audio, volume)
        if note and gizmo and note.startswith("the render is silent"):
            note = "the render is silent. Does the MIDI map play this kit's drums with the MIDI's notes (--midimap)?"
        if note and opts["--carla"] and note.startswith("the render is silent"):
            note = "the render is silent. Does the plugin need a kit loaded? Load it in Carla and save the project again."
        if note:
            print("Note: " + note)
        if not out:
            out = "%s-%s.wav" % (stem(source), stem(name))
        if opts["--outdir"]:
            if not os.path.isdir(opts["--outdir"]):
                os.makedirs(opts["--outdir"])
            out = os.path.join(opts["--outdir"], os.path.basename(out))
        write_wav(out, audio, rate, bits)
        print("Wrote %s: %.1f s, %d Hz, %d-bit stereo" % (out, audio.shape[1] / float(rate), rate, bits))
        batch_done = [(midi, out)]
    else:
        print("vst_render: %d MIDI file%s in %s%s%s" % (len(batch), "" if len(batch) == 1 else "s", folder, " and " + opts["--dir2"] if two else "", " (and their subfolders)" if two and "--recursive" in switches else " (and its subfolders)" if "--recursive" in switches else ""))
        batch_done = []
        skip = "--skip_existing" in switches
        kept = 0
        for m in stoppable(batch, "rendering", "the test and the zip use the WAVs made"):
            where = opts["--outdir"] or os.path.dirname(m)
            if opts["--outdir"] and (not pls or roots.get(m) != folder):
                # Subfolders are kept under --outdir (with two folders, under each one's name).
                where = os.path.join(opts["--outdir"], os.path.dirname(rel(m)))
            o = os.path.join(where, "%s-%s.wav" % (stem(m), stem(name)))
            # --skip_existing: a WAV a stopped run made is kept, when it is whole.
            if skip and whole_wav(o):
                print("Kept %s (already made)" % shown(o))
                batch_done.append((m, o))
                kept += 1
                continue
            # An MP3 of it from a --mp3 run (its WAV deleted): the MIDI's render too.
            made_mp3 = os.path.splitext(o)[0] + ".mp3"
            if skip and os.path.isfile(made_mp3) and os.path.getsize(made_mp3) > 0:
                print("Kept %s (already made)" % shown(made_mp3))
                batch_done.append((m, made_mp3))
                kept += 1
                continue
            try:
                audio, length, _ = chain.render(np, midi=at_tempo(m), tail=tail)
            except Exception as e:
                print("Failed %s: %s" % (rel(m), e))
                continue
            audio, note = finish(np, audio, volume)
            if not os.path.isdir(where):
                os.makedirs(where)
            write_wav(o, audio, rate, bits)
            print("Wrote %s: %.1f s%s%s" % (shown(o), audio.shape[1] / float(rate), tempo_note(m), " (" + note + ")" if note else ""))
            batch_done.append((m, o))
    dms = []
    rep = None
    # The plugin's (or project's, kit's) name: the test loop's `name` is each WAV's.
    plugin_name = name
    if test:
        report = ["vst_render --test: DrumMidi_CRT on each render, its hits against the MIDI played",
                  "Plugin: %s%s" % (os.path.basename(os.path.normpath(name)), ", effect " + os.path.basename(os.path.normpath(opts["--effect"])) if opts["--effect"] else ""),
                  "Notes mapped (--map%s): " % ("" if map_part is None else ", section [%s]" % map_part if map_part else ", no section for this plugin") + (", ".join("%d=%s" % (a, "none" if b is None else b) for a, b in sorted(nmap.items())) if nmap else "none"),
                  "P: DrumMidi's hits that were played, R: played hits DrumMidi found (within %d ms, after the best shift)" % (TOLERANCE * 1000), ""]
        total = {}
        tested = 0
        for m, o in stoppable(batch_done, "the test", "the report has the WAVs tested"):
            tested += 1
            dm = os.path.splitext(o)[0] + "-dm.mid"
            no_toms = "--no-toms" in switches
            if "--skip_existing" in switches and os.path.isfile(dm) and os.path.getmtime(dm) >= os.path.getmtime(o):
                # DrumMidi's MIDI of this WAV from a stopped run: used as it is.
                found = midi_notes(dm)[1]
                print("Kept %s (already made)" % os.path.basename(dm))
            else:
                found = drum_midi(opts["--java"], opts["--drummidi"], o, dm, (["--no-toms"] if no_toms else []) + (["--snare-leak"] if "--snare-leak" in switches else []))
            if os.path.isfile(dm):
                dms.append(dm)
            name = os.path.basename(o)
            if isinstance(found, str):
                report.append("%s: %s" % (name, found))
                continue
            length, played = midi_notes(at_tempo(m))
            played = mapped(played, nmap)
            if no_toms:
                played = [(t, n) for t, n in played if n not in TOMS]
            rows, shift = score(played, found, length + 0.05)
            if no_toms:
                rows = [r for r in rows if r[0] != "Toms"]
            for fam, t, f, ok in rows:
                v = total.setdefault(fam, [0, 0, 0])
                v[0] += t
                v[1] += f
                v[2] += ok
            report.append("%s (shift %+d ms): %s" % (name, round(shift * 1000), score_line(rows)))
        if tested < len(batch_done):
            report.append("Stopped: %d of %d WAVs tested" % (tested, len(batch_done)))
        if total:
            report.append("")
            report.append("All: " + score_line([(fam, total[fam][0], total[fam][1], total[fam][2]) for fam, notes in FAMILIES if fam in total]))
        text = "\n".join(report)
        print("")
        print(text)
        where = opts["--outdir"] or (folder if batch is not None else os.path.dirname(os.path.abspath(batch_done[0][1])))
        rep = os.path.join(where, "vst-test-%s.txt" % stem(plugin_name))
        with open(rep, "w") as f:
            f.write(text + "\n")
        print("Wrote %s" % rep)
    if mp3_kbps and batch_done:
        wav_mb = mp3_mb = 0.0
        converted = 0
        for k in stoppable(list(range(len(batch_done))), "MP3", "the rest stay WAVs"):
            m, o = batch_done[k]
            if o.lower().endswith(".mp3"):
                continue
            mp3 = os.path.splitext(o)[0] + ".mp3"
            why = to_mp3(opts["--java"], opts["--drummidi"], o, mp3, mp3_kbps)
            if why:
                print("MP3 failed %s: %s (the WAV stays)" % (os.path.basename(o), why))
                continue
            wav_mb += os.path.getsize(o) / float(ZIP_MB)
            mp3_mb += os.path.getsize(mp3) / float(ZIP_MB)
            if "--keep_wav" not in switches:
                os.remove(o)
            batch_done[k] = (m, mp3)
            converted += 1
            print("MP3 %s: %.2f MB" % (os.path.basename(mp3), os.path.getsize(mp3) / float(ZIP_MB)))
        if converted:
            print("MP3s: %d at %d kbps, %.1f MB instead of %.1f MB of WAVs%s" % (converted, mp3_kbps, mp3_mb, wav_mb, " (the WAVs kept)" if "--keep_wav" in switches else ""))
    where = opts["--outdir"] or (folder if batch is not None else os.path.dirname(os.path.abspath(batch_done[0][1]))) if batch_done else None
    limit_mb = zip_mb if "--zip" in switches or "--zip_nowav" in switches or zip_mb else (ask_zip() if batch is not None or test else None)
    if where and limit_mb is not None:
        made = make_zip(where, stem(plugin_name), argv, folder if batch is not None else None,
                        [(m, played_as.get(m)) for m, _ in batch_done] if not opts["--wav"] else [(opts["--wav"], None)],
                        [o for _, o in batch_done], dms, rep, opts, None if by_name else bpm, limit_mb, "--zip_nowav" in switches,
                        rel if two else None)
        for z in made:
            print("Zip: %s (%.1f MB)" % (z, os.path.getsize(z) / float(ZIP_MB)))
    if batch is not None:
        made = len(batch_done) - kept
        print("Succeeded: %d of %d MIDI files rendered%s" % (made, len(batch), ", %d kept from before" % kept if kept else ""))
    else:
        print("Succeeded: %s" % batch_done[0][1])


if __name__ == "__main__":
    main(sys.argv[1:])
