"""drumalong_bench.py <bench dir> <DrumAlong.jar> [extra DrumAlong args]: DrumAlong on each band stem against
DrumMidi on the same song's drums stem (the drummer Sogni played): tempo, beats, kick and snare F, and on which
of DrumAlong's beats the drummer's snares fall.

<bench dir>/band/<song>.mp3 and <bench dir>/drums/<song>.mp3: SogniMusic's band sessions (drums_bass,
drums_guitar, drums_bass_guitar, drums_piano, drums_synth) split by Demucs (--two-stems=drums: no_drums and
drums). DrumMidi's results are kept in <bench dir>/ref, DrumAlong's in <bench dir>/est, all in result.json.

18.11.2026, 250 tracks: tempo right 68% (twice or half 10%), beats on the drummer's 59%; where tempo and
beats are right, the bar's first beat right on about half (the snare then on the drummer's 78%)."""
import sys, os, re, glob, json, subprocess, importlib.util, concurrent.futures as cf
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
spec = importlib.util.spec_from_file_location("vr", os.path.join(ROOT, "Programs", "Python", "vst_render.py"))
vr = importlib.util.module_from_spec(spec); spec.loader.exec_module(vr)
B, CP = sys.argv[1], sys.argv[2]
EXTRA = sys.argv[3:]
JAR = os.path.join(ROOT, "Programs", "Java", "DrumMidi_CRT.jar")
os.makedirs(B + "/ref", exist_ok=True); os.makedirs(B + "/est", exist_ok=True)
KICK, SNARE = {35, 36}, {37, 38, 40}

def ref(n):
    mid = B + "/ref/" + n + ".mid"; info = B + "/ref/" + n + ".txt"
    if not os.path.exists(info):
        r = subprocess.run(["java", "-jar", JAR, B + "/drums/" + n + ".mp3", mid], capture_output=True, text=True)
        m = re.search(r"Tempo: ([\d.]+) BPM, first beat near ([\d.]+) s", r.stdout)
        open(info, "w").write("%s %s" % (m.group(1), m.group(2)) if m else "0 0")
    bpm, ph = map(float, open(info).read().split())
    return bpm, ph, (vr.midi_notes(mid)[1] if os.path.exists(mid) else [])

def est(n):
    mid = B + "/est/" + n + ".mid"
    r = subprocess.run(["java", "-cp", CP, "DrumAlong", B + "/band/" + n + ".mp3", mid] + EXTRA, capture_output=True, text=True)
    m = re.search(r"Tempo: ([\d.]+) BPM, first bar at ([\d.]+) s, (\d+) bars of (\S+)( \(swung\))?", r.stdout)
    st = re.search(r"Style: (.*?) \(", r.stdout)
    if not m or not os.path.exists(mid): return None
    return float(m.group(1)), float(m.group(2)), m.group(4), bool(m.group(5)), st.group(1) if st else "", vr.midi_notes(mid)[1]

def f1(a, b, tol=0.07):
    a = sorted(a); b = sorted(b); used = set(); tp = 0
    for t in a:
        best = None
        for j, u in enumerate(b):
            if j not in used and abs(u - t) <= tol and (best is None or abs(u - t) < abs(b[best] - t)): best = j
        if best is not None: used.add(best); tp += 1
    return tp, len(a), len(b)

def one(path):
    n = os.path.basename(path)[:-4]
    s = re.search(r"-(drums_bass_guitar|drums_bass|drums_guitar|drums_piano|drums_synth)-", n).group(1)
    rb, rp, rn = ref(n)
    e = est(n)
    if e is None or rb == 0: return None
    eb, ep, meter, swung, style, en = e
    ratio = eb / rb
    tempo = "ok" if abs(ratio - 1) < 0.04 else "x2" if abs(ratio - 2) < 0.08 or abs(ratio - 0.5) < 0.04 else "off"
    # Beats: DrumAlong's quarter grid against DrumMidi's.
    per = 60 / rb; dur = 10.0
    rbeats = [rp + k * per for k in range(-2, int(dur / per) + 2)]
    ebeats = [ep + k * 60 / eb for k in range(-8, int(dur * eb / 60) + 2) if 0 <= ep + k * 60 / eb <= dur]
    on = sum(1 for t in ebeats if min(abs(t - u) for u in rbeats) < 0.07) / max(1, len(ebeats))
    # The drummer's snares on DrumAlong's bars: on its beats 2 and 4 (right), 1 and 3 (bar start off by a beat).
    bpb = int(meter.split("/")[0]) if meter.endswith("/4") else 2
    p2 = 60 / eb; bb = bp = 0; pos = [0] * bpb
    for t, x in rn:
        if x not in SNARE: continue
        k_ = round((t - ep) / p2)
        if abs(t - (ep + k_ * p2)) > 0.07: continue
        if k_ % 2 == 1: bb += 1
        else: bp += 1
        pos[k_ % bpb] += 1
    k = f1([t for t, x in en if x in KICK], [t for t, x in rn if x in KICK])
    sn = f1([t for t, x in en if x in SNARE], [t for t, x in rn if x in SNARE])
    return {"pos": pos, "ep": ep, "eb": eb, "bb": bb, "bp": bp, "n": n, "s": s, "tempo": tempo, "beats": on, "kick": k, "snare": sn, "meter": meter, "swung": swung, "style": style}

files = sorted(glob.glob(B + "/band/*.mp3"))
with cf.ThreadPoolExecutor(4) as ex:
    rows = [r for r in ex.map(one, files) if r]
json.dump(rows, open(B + "/result.json", "w"))
import collections
agg = collections.defaultdict(lambda: collections.Counter())
for r in rows:
    for g in (r["s"], "all"):
        a = agg[g]; a["n"] += 1; a["tempo_" + r["tempo"]] += 1; a["beats"] += r["beats"]
        for d in ("kick", "snare"):
            tp, ne, nr = r[d]; a[d + "_tp"] += tp; a[d + "_e"] += ne; a[d + "_r"] += nr
        a["swung"] += r["swung"]; a["m34"] += r["meter"] != "4/4"; a["bb"] += r["bb"]; a["bp"] += r["bp"]
for g in ["drums_bass", "drums_guitar", "drums_bass_guitar", "drums_piano", "drums_synth", "all"]:
    a = agg[g]
    if not a["n"]: continue
    F = lambda d: 200 * a[d + "_tp"] / max(1, a[d + "_e"] + a[d + "_r"])
    P = lambda d: 100 * a[d + "_tp"] / max(1, a[d + "_e"])
    R = lambda d: 100 * a[d + "_tp"] / max(1, a[d + "_r"])
    print("%-18s n %3d  tempo ok %3.0f%% x2 %3.0f%%  beats on %3.0f%%  kick P%3.0f R%3.0f F%3.0f  snare P%3.0f R%3.0f F%3.0f  swung %d, not 4/4 %d, drummer's snares on 2/4 %3.0f%%" % (
        g, a["n"], 100 * a["tempo_ok"] / a["n"], 100 * a["tempo_x2"] / a["n"], 100 * a["beats"] / a["n"],
        P("kick"), R("kick"), F("kick"), P("snare"), R("snare"), F("snare"), a["swung"], a["m34"], 100 * a["bb"] / max(1, a["bb"] + a["bp"])))
