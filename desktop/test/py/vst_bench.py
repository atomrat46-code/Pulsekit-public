# vst_render --model_compare and --leave_one_out: two synthesized kits' test renders (closed hats, a ride and
# crashes over a kick and snare, a brighter and a darker kit), each zipped as a test run zips them (WAVs,
# MIDI, report, kit-less), the second in a newer run's folder; the bench run on them and its zips' summaries
# printed. Used by DesktopBehavior s254. Arguments: vst_render.py, DrumMidi_CRT.jar, a work folder.
import importlib.util
import os
import sys
import types
import zipfile

import numpy as np

sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')
spec = importlib.util.spec_from_file_location('vr', os.path.abspath(sys.argv[1]))
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)
jar, work = os.path.abspath(sys.argv[2]), os.path.abspath(sys.argv[3])
R = 44100


def midi(path, beats, tpq=480):
    """A drum MIDI at 120 of (beat, note) hits, several on a beat too (each 10 ticks long)."""
    import struct
    ev = sorted([(int(b * tpq), 1, n) for b, n in beats] + [(int(b * tpq) + 10, 0, n) for b, n in beats])
    data, at = b"\x00\xff\x51\x03\x07\xa1\x20", 0
    for tick, on, n in ev:
        d, v = tick - at, []
        v.append(d & 0x7f)
        d >>= 7
        while d:
            v.insert(0, (d & 0x7f) | 0x80)
            d >>= 7
        data += bytes(v) + bytes([0x99 if on else 0x89, n, 110 if on else 0])
        at = tick
    data += b"\x00\xff\x2f\x00"
    with open(path, "wb") as f:
        f.write(b"MThd" + struct.pack(">IHHH", 6, 0, 1, tpq) + b"MTrk" + struct.pack(">I", len(data)) + data)


def hit(note, bright, rng):
    """A drum's sound: kick and snare bursts, a short hat, a ringing ride with a ping, a long crash."""
    ring = {36: 0.15, 38: 0.12, 42: 0.04, 51: 0.7, 49: 1.4}[note]
    n = int((ring * 3 + 0.05) * R)
    t = np.arange(n) / R
    env = np.exp(-t / ring)
    if note == 36:
        return 0.9 * np.sin(2 * np.pi * 60 * t) * env
    noise = rng.standard_normal(n)
    if note == 38:
        return 0.5 * np.diff(noise, prepend=0) * env
    hp = np.diff(np.diff(noise, prepend=0), prepend=0)
    if note == 51:
        return 0.12 * hp * env * bright + 0.15 * np.sin(2 * np.pi * (5200 if bright > 1 else 3600) * t) * env
    return (0.2 if note == 42 else 0.25) * hp * env * bright


def song(i, bright):
    """Eight bars at 120: kick and snare, hats (or a ride in bars 5 to 8) in eighths, a crash at bar 1 and 5."""
    rng = np.random.default_rng(i * 10 + int(bright * 3))
    beats = []
    for bar in range(8):
        for e in range(8):
            b = bar * 4 + e * 0.5
            beats.append((b, 51 if bar >= 4 and i % 2 else 42))
            if e in (0, 4):
                beats.append((b, 36))
            if e in (2, 6):
                beats.append((b, 38))
        if bar in (0, 4):
            beats.append((bar * 4, 49))
    out = np.zeros(int(18 * R))
    for b, n in beats:
        x = hit(n, bright, rng)
        a = int(b * 0.5 * R)
        out[a:a + len(x)] += x[:len(out) - a]
    return sorted(beats), out * 0.5


for run, (kit, bright) in enumerate((("Bright", 1.6), ("Dark", 0.7))):
    folder = os.path.join(work, "runs", str(run + 1))
    os.makedirs(folder, exist_ok=True)
    name = "Fake Drums (%s)" % kit
    z = zipfile.ZipFile(os.path.join(folder, "vst-render-Fake_Drums_%s_-2026111%d-120000.zip" % (kit, run + 1)), "w")
    for i in range(4):
        beats, audio = song(i, bright)
        mid, wav = os.path.join(work, "s%d.mid" % i), os.path.join(work, "s%d.wav" % i)
        midi(mid, beats)
        vr.write_wav(wav, np.vstack([audio, audio]), R, 16)
        z.write(mid, "midi/songs/groove%d.mid" % i)
        z.write(wav, "wav/songs/groove%d-%s.wav" % (i, name))
    z.writestr("vst-test-%s.txt" % name, "vst_render --test\nPlugin: Fake Drums, kit %s\nNotes mapped (--map, none): none\n" % kit)
    z.writestr("vst-render-run.txt", "vst_render.py --plugin Fake --test --faded-opens\n")
    z.close()
out = os.path.join(work, "out")
try:
    vr.main(["--dir", os.path.join(work, "runs"), "--recursive", "--outdir", out, "--model_compare", "builtin,all", "--leave_one_out", "all",
             "--drummidi", jar, "--zip_outdir", os.path.join(work, "copies")])
except SystemExit as e:
    print("exit", e.code)
for f in sorted(os.listdir(out)):
    if f.endswith(".zip"):
        with zipfile.ZipFile(os.path.join(out, f)) as z:
            print("zip:", f.split("-2026")[0], sorted(z.namelist()))
            for line in z.read("summary.txt").decode().splitlines():
                print("  " + line)
print("copies:", sorted(f.split("-2026")[0] for f in os.listdir(os.path.join(work, "copies"))))
# Again: DrumMidi's results kept (no run of it this time).
vr.main(["--dir", os.path.join(work, "runs"), "--recursive", "--outdir", out, "--model_compare", "none,builtin", "--drummidi", jar])
