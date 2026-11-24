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
# vst_render's ~/.pulsekit (kit-names.txt) in the work folder, not the real one.
os.environ['HOME'] = os.environ['USERPROFILE'] = work
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
# A run left out by its date and time: the kit's older run counts instead (a run with silent notes).
older = os.path.join(work, "older")
os.makedirs(older, exist_ok=True)
for stamp in ("20261101-120000", "20261112-120000"):
    zipfile.ZipFile(os.path.join(older, "vst-render-Fake_Drums_Dark_-%s.zip" % stamp), "w").close()
left = []
picked = vr.render_kits(older, False, skip=["20261112-120000"], left=left)
print("run skipped: %s | left %s | kit skipped: %s" % ([(k[0], k[1]) for k in picked], left, vr.render_kits(older, False, skip=["fake drums dark"])))
out = os.path.join(work, "out")
try:
    vr.main(["--cymbal_bench", "--bench_dir", os.path.join(work, "runs"), "--bench_unzip", os.path.join(work, "unzip"), "--outdir", out, "--model_compare",
             "--model_a", "builtin", "--model_b", "all", "--leave_one_out", "--loo_kits", "all", "--drummidi", jar, "--zip_outdir", os.path.join(work, "copies")])
except SystemExit as e:
    print("exit", e.code)
for f in sorted(os.listdir(out)):
    if f.endswith(".zip"):
        with zipfile.ZipFile(os.path.join(out, f)) as z:
            print("zip:", f.split("-2026")[0], sorted(z.namelist()))
            for line in z.read("summary.txt").decode().splitlines():
                print("  " + line)
print("copies:", sorted(f.split("-2026")[0] for f in os.listdir(os.path.join(work, "copies"))))
# --zip_outdir with %n: the newest numbered folder there gets the comparison's zip, no new one made.
os.makedirs(os.path.join(work, "numbered", "1"))
os.makedirs(os.path.join(work, "numbered", "2"))
vr.main(["--cymbal_bench", "--bench_dir", os.path.join(work, "runs"), "--bench_unzip", os.path.join(work, "unzip"), "--outdir", out, "--model_compare",
         "--model_b", "none", "--drummidi", jar, "--zip_outdir", os.path.join(work, "numbered", "%n")])
print("numbered:", sorted(os.listdir(os.path.join(work, "numbered"))), [len(os.listdir(os.path.join(work, "numbered", d))) for d in ("1", "2")])
# The unzip folder: each kit's MIDI and report kept, its renders deleted once DrumMidi's results are kept.
unz = os.path.join(work, "unzip")
for k in sorted(os.listdir(unz)):
    print("unzipped:", k, "renders", os.path.isdir(os.path.join(unz, k, "wav")), "midi", os.path.isdir(os.path.join(unz, k, "midi")))
print("unzip folder outside the output folder:", not os.path.isdir(os.path.join(out, "cymbal-bench", "kits")))
# Settings' Cymbal model comparison off: the tab's parts are not run.
try:
    vr.main(["--bench_dir", os.path.join(work, "runs"), "--outdir", out, "--model_compare", "--drummidi", jar])
except SystemExit as e:
    print("off: exit", e.code)
# Run last: after the run (here it fails: no plugin), the comparison still goes; the run's exit code kept.
try:
    vr.main(["--cymbal_bench", "--cymbal_bench_when", "last", "--bench_dir", os.path.join(work, "runs"), "--bench_unzip", unz, "--outdir", out,
             "--model_compare", "--drummidi", jar])
except SystemExit as e:
    print("last: exit", e.code)
# Again: DrumMidi's results kept (no run of it this time).
# (v434's way of giving the models still taken.)
vr.main(["--dir", os.path.join(work, "runs"), "--outdir", out, "--model_compare", "none,builtin", "--drummidi", jar])
# Model A from the older kits (those before the new one), B from the first 2 kits; a wrong N said plainly.
vr.main(["--cymbal_bench", "--bench_dir", os.path.join(work, "runs"), "--bench_unzip", unz, "--outdir", out, "--model_compare", "--model_a", "older", "--model_b", "kits", "--model_b_kits", "2",
         "--drummidi", jar])
# Kits left out by parts of their names: one kit is not enough to compare.
try:
    vr.main(["--cymbal_bench", "--bench_dir", os.path.join(work, "runs"), "--bench_unzip", unz, "--outdir", out, "--model_compare", "--drummidi", jar,
             "--bench_skip", "fake drums bright\n# a comment\nNothing"])
except SystemExit as e:
    print("skip: exit", e.code)
for bad in (["--model_b", "kits", "--model_b_kits", "5"], ["--model_b", "kits"], ["--leave_one_out", "--loo_kits", "named"]):
    try:
        vr.main(["--cymbal_bench", "--bench_dir", os.path.join(work, "runs"), "--bench_unzip", unz, "--outdir", out, "--model_compare", "--drummidi", jar] + bad)
    except SystemExit as e:
        print("refused", e.code)
# The hit model bench (the Hit model tab): each kit with DrumMidi's rules alone and with its hit model; leave one
# out (one plugin: kit by kit) and the model learned from both, then that model file used.
hout = os.path.join(work, "hits-out")
vr.main(["--hit_bench", "--hit_loo", "--hit_dir", os.path.join(work, "runs"), "--bench_unzip", unz, "--outdir", hout, "--drummidi", jar])
learned = [f for f in sorted(os.listdir(hout)) if f.startswith("hit-model-") and f.endswith(".txt")]
for f in sorted(os.listdir(hout)):
    if f.endswith(".zip"):
        with zipfile.ZipFile(os.path.join(hout, f)) as z:
            print("hit zip:", f.split("-2026")[0], sorted(z.namelist()))
            for line in z.read("summary.txt").decode().splitlines()[:3]:
                print("  " + line)
print("hit model file:", len(learned), open(os.path.join(hout, learned[0])).read().split("\n")[0] if learned else "")
vr.main(["--hit_bench", "--hit_dir", os.path.join(work, "runs"), "--bench_unzip", unz, "--outdir", hout, "--drummidi", jar, "--hit_model_file", os.path.join(hout, learned[0])])
# A kit named again (KVLT_Mexico 1998, then KVLT: Mexico 1998): one kit, its newest run's.
again = os.path.join(work, "again")
os.makedirs(again, exist_ok=True)
for n in ("vst-render-Ugritone_Drums_KVLT_Mexico_1998_-20261101-100000.zip", "vst-render-Ugritone_Drums_KVLT__Mexico_1998_-20261116-100000.zip"):
    open(os.path.join(again, n), "w").write("x")
print("named again:", [(k, s) for k, s, z, w in vr.render_kits(again, True)])
# A kit given a pack later (1983 Metal, then Speed Metal: 1983 Metal): one kit with kits.txt's alias line.
for n in ("vst-render-Ugritone_Drums_1983_Metal_-20261101-100000.zip", "vst-render-Ugritone_Drums_Speed_Metal__1983_Metal_-20261116-100000.zip"):
    open(os.path.join(again, n), "w").write("x")
print("without alias:", len(vr.render_kits(again, True, {})))
os.makedirs(os.path.join(work, ".pulsekit", "maps"), exist_ok=True)
open(os.path.join(work, ".pulsekit", "maps", "kits.txt"), "w").write("[Ugritone Drums]\nSpeed Metal: 1983 Metal = 1983 Metal\n")
print("with alias:", [(k, s) for k, s, z, w in vr.render_kits(again, True)])
# Both benches in one run (run separately): one unzip folder, each kit's renders unpacked once and deleted after the cymbal bench.
import contextlib
import io
# (The earlier benches' work folders forgotten here and below: their kept results would be copied, and DrumMidi would not run.)
works_file = os.path.join(os.path.expanduser("~"), ".pulsekit", vr.BENCH_WORKS)
if os.path.isfile(works_file):
    os.remove(works_file)
bout = os.path.join(work, "both-out")
said = io.StringIO()
with contextlib.redirect_stdout(said):
    vr.main(["--hit_bench", "--cymbal_bench", "--model_compare", "--hit_dir", os.path.join(work, "runs"), "--bench_dir", os.path.join(work, "runs"),
             "--outdir", bout, "--drummidi", jar])
text = said.getvalue()
shared = os.path.join(bout, "cymbal-bench", "kits")
left = [d for d in os.listdir(shared) if os.path.isdir(os.path.join(shared, d)) and any(os.path.isdir(os.path.join(shared, d, s)) for s in ("mp3", "wav"))]
print("both: unpacked %d times, shared said %s, hit bench's own unzip folder %s, renders left %s, cymbal kits %d" % (
    text.count("unpacking"), "Unzip folder shared with the cymbal model comparison" in text, os.path.isdir(os.path.join(bout, "hit-bench", "kits")), left,
    text.count("DrumMidi ran on each")))
# --bench_keep: the renders kept after the bench; a bench run again (its kept results gone, another DrumMidi) unpacks nothing.
kout = os.path.join(work, "keep-out")
if os.path.isfile(works_file):
    os.remove(works_file)
said = io.StringIO()
with contextlib.redirect_stdout(said):
    vr.main(["--cymbal_bench", "--model_compare", "--bench_keep", "--bench_dir", os.path.join(work, "runs"), "--outdir", kout, "--drummidi", jar])
first = said.getvalue().count("unpacking")
kunz = os.path.join(kout, "cymbal-bench", "kits")
kept = sorted(d for d in os.listdir(kunz) if any(os.path.isdir(os.path.join(kunz, d, s)) for s in ("mp3", "wav")))
import shutil
shutil.rmtree(os.path.join(kout, "cymbal-bench", "data"))
if os.path.isfile(works_file):
    os.remove(works_file)
said = io.StringIO()
with contextlib.redirect_stdout(said):
    vr.main(["--cymbal_bench", "--model_compare", "--bench_keep", "--bench_dir", os.path.join(work, "runs"), "--outdir", kout, "--drummidi", jar])
print("keep: unpacked %d, renders kept %d, again unpacked %d, DrumMidi ran %d" % (first, len(kept), said.getvalue().count("unpacking"), said.getvalue().count("DrumMidi ran on each")))
# Another output folder (so another work folder): the earlier bench's kept results are found and copied, DrumMidi does not run.
said = io.StringIO()
with contextlib.redirect_stdout(said):
    vr.main(["--cymbal_bench", "--model_compare", "--bench_dir", os.path.join(work, "runs"), "--outdir", os.path.join(work, "elsewhere-out"), "--drummidi", jar])
line = [l for l in said.getvalue().splitlines() if l.startswith("DrumMidi's results kept:")]
print("elsewhere: %s | DrumMidi ran %d" % (line[0].split(" (")[0] if line else "-", said.getvalue().count("DrumMidi ran on each")))
# Several input folders, separated by ";": each one's kits found (the runs' two folders given apart).
twodirs = "%s; %s" % (os.path.join(work, "runs", "1"), os.path.join(work, "runs", "2"))
print("folders:", vr.bench_folders(twodirs) == [os.path.join(work, "runs", "1"), os.path.join(work, "runs", "2")],
      "kits:", sorted(k[0] for k in vr.render_kits(twodirs, True)))
