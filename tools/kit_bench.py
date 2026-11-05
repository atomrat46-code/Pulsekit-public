"""kit_bench.py render <kitdir> <name> <midis> <out> | score <out> [jar] [extra switches...]
A DrumMidi bench on free DrumGizmo kits (drumgizmo.org/kits), as vst_render --test does for a plugin.

render: the test beat (vst_render's check_midi: a groove, then the kick, toms and cymbals alone) and
each MIDI of <midis> are played through the kit (drumgizmo_render.py; notes the kit's map lacks are
moved first, drumgizmo_remap.py) into <out>/<name>/: WAVs, the MIDI as played (scored against), and
the kit profile DrumMidi learns from the test beat (kit.kit). The kit itself can go after this.

score: DrumMidi_CRT (the jar given, else Programs/Java's) on every WAV of every kit in <out>, without
and with its kit profile, scored like vst_render --test (vst_render's score and cymbal breakdown):
one line a kit and both ways, then the totals.
"""
import glob
import importlib.util
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
spec = importlib.util.spec_from_file_location("vr", os.path.join(ROOT, "Programs", "Python", "vst_render.py"))
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)


def kit_files(kitdir):
    """The kit's drumkit XML and MIDI map."""
    xmls = [f for f in glob.glob(os.path.join(kitdir, "**", "*.xml"), recursive=True)]
    kit = mmap = None
    for f in sorted(xmls, key=len):
        head = open(f, "rb").read(4000).decode("utf-8", "replace")
        if kit is None and "<drumkit" in head:
            kit = f
        if mmap is None and "<midimap" in head:
            mmap = f
    if kit is None or mmap is None:
        sys.exit("no drumkit or midimap XML in %s" % kitdir)
    return kit, mmap


def render(kitdir, name, midis, out):
    kit, mmap = kit_files(kitdir)
    where = os.path.join(out, name)
    os.makedirs(where, exist_ok=True)
    work = os.path.join(where, "work")
    os.makedirs(work, exist_ok=True)
    beat = vr.check_midi(work)
    todo = [("check", beat)] + [(os.path.splitext(os.path.basename(m))[0], m) for m in sorted(glob.glob(os.path.join(midis, "*.mid")))]
    for i, (stem, mid) in enumerate(todo):
        played = os.path.join(where, stem + ".mid")
        wav = os.path.join(where, stem + ".wav")
        if os.path.isfile(wav):
            continue
        subprocess.run([sys.executable, os.path.join(HERE, "drumgizmo_remap.py"), mid, played, mmap], check=True, stdout=subprocess.DEVNULL)
        subprocess.run([sys.executable, os.path.join(HERE, "drumgizmo_render.py"), kit, mmap, played, wav, "full", str(i + 1)], check=True,
                       stdout=subprocess.DEVNULL)
        print("%s: %s (%d of %d)" % (name, stem, i + 1, len(todo)), flush=True)
    # The kit profile, from the test beat as played (its second part: each drum alone).
    times = os.path.join(work, "times.txt")
    with open(times, "w") as f:
        # The beat's cymbals struck with a kick or snare, or played on in eighths (kit_events' tags): tagged; the kick or snare left out.
        tags = {round(t, 2): tag for t, n, _, tag in vr.kit_events() if tag in ("k", "s", "g")}
        for t, n in vr.midi_notes(os.path.join(where, "check.mid"))[1]:
            tag = tags.get(round(t, 2), "")
            if t < 1.99 or (tag and n not in vr.KIT_CYMBALS):
                continue
            if n == 36 or n in vr.TOMS or n in vr.KIT_CYMBALS:
                f.write("%.4f %d%s\n" % (t, n, " " + tag if tag else ""))
    jar = os.path.join(ROOT, "Programs", "Java", "DrumMidi_CRT.jar")
    r = subprocess.run(["java", "-cp", jar, "DrumMidi_CRT", "--learn-kit", os.path.join(where, "check.wav"), times, os.path.join(where, "kit.kit")],
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(name + ": " + (r.stdout.decode("utf-8", "replace").strip().splitlines() or ["no answer"])[-1], flush=True)
    shutil.rmtree(work, ignore_errors=True)


def score(out, jar, extra):
    totals = {}
    lines = []
    for kitdir in sorted(d for d in glob.glob(os.path.join(out, "*")) if os.path.isdir(d)):
        name = os.path.basename(kitdir)
        kit = os.path.join(kitdir, "kit.kit")
        for way, switches in (("plain", list(extra)), ("kit", list(extra) + ["--kit", kit])):
            fam = {}
            cym = [[0] * (len(vr.CYMBAL_KINDS) + 1) for _ in vr.CYMBAL_KINDS]
            cex = [0] * len(vr.CYMBAL_KINDS)
            for wav in sorted(glob.glob(os.path.join(kitdir, "*.wav"))):
                stem = os.path.splitext(os.path.basename(wav))[0]
                if stem == "check" or stem.endswith("-dm"):
                    continue
                length, played = vr.midi_notes(os.path.join(kitdir, stem + ".mid"))
                dm = os.path.join(kitdir, stem + "-dm.mid")
                found = vr.drum_midi("java", jar, wav, dm, switches)
                if isinstance(found, str):
                    lines.append("%s %s %s: %s" % (name, way, stem, found))
                    continue
                rows, shift = vr.score(played, found, length + 0.05)
                for f, t, n, ok in rows:
                    v = fam.setdefault(f, [0, 0, 0])
                    v[0] += t
                    v[1] += n
                    v[2] += ok
                table, extra_hits = vr.cymbal_breakdown(played, found, length + 0.05, shift)
                for a in range(len(table)):
                    for b in range(len(table[a])):
                        cym[a][b] += table[a][b]
                    cex[a] += extra_hits[a]
            row = [(f, fam[f][0], fam[f][1], fam[f][2]) for f, _ in vr.FAMILIES if f in fam]
            right = []
            for a, (kind, _) in enumerate(vr.CYMBAL_KINDS):
                tot = sum(cym[a])
                right.append("%s %s" % (kind, "%d%%" % round(100.0 * cym[a][a] / tot) if tot else "-"))
            lines.append("%-14s %-5s %s" % (name, way, vr.score_line(row)))
            lines.append("%-14s %-5s right: %s" % ("", "", ", ".join(right)))
            t = totals.setdefault(way, [{}, [[0] * (len(vr.CYMBAL_KINDS) + 1) for _ in vr.CYMBAL_KINDS], [0] * len(vr.CYMBAL_KINDS)])
            for f, v in fam.items():
                w = t[0].setdefault(f, [0, 0, 0])
                for k in range(3):
                    w[k] += v[k]
            for a in range(len(cym)):
                for b in range(len(cym[a])):
                    t[1][a][b] += cym[a][b]
                t[2][a] += cex[a]
    print("\n".join(lines))
    for way, (fam, cym, cex) in totals.items():
        print("\nAll, %s: %s" % (way, vr.score_line([(f, fam[f][0], fam[f][1], fam[f][2]) for f, _ in vr.FAMILIES if f in fam])))
        print("\n".join(vr.breakdown_text(cym, cex)))


if __name__ == "__main__":
    if len(sys.argv) >= 6 and sys.argv[1] == "render":
        render(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
    elif len(sys.argv) >= 3 and sys.argv[1] == "score":
        score(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 and sys.argv[3].endswith(".jar") else os.path.join(ROOT, "Programs", "Java", "DrumMidi_CRT.jar"),
              [a for a in sys.argv[3:] if not a.endswith(".jar")])
    else:
        sys.exit(__doc__)
