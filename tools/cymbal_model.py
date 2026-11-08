"""cymbal_model.py: DrumMidi's cymbal model (DrumMidi_CRT.CymModel), learned from vst_render test runs.

  python tools/cymbal_model.py data   <kits.txt> <work> [jar]   the training data, a .npz a kit
  python tools/cymbal_model.py check  <work> [margin]            how it does on kits (and songs) it never saw
  python tools/cymbal_model.py export <work> [--write]           the model from every kit; --write puts it in DrumMidi
  python tools/cymbal_model.py bench  <kits.txt> <work> [jar]    DrumMidi itself, each kit with a model learned without it

kits.txt: a kit a line, tab-separated: a short name, the folder its vst-render zips were unzipped
into (mp3/ and midi/ under it, as the zips hold them), its vst_render report (vst-test-*.txt: the
notes map, --map, is read from it) and optionally its kit profile (vst-kit-*.kit), used with --kit
as the test does. "#" lines are left out. E.g. (the 8.11. tests the model was learned from):

  KVLT       renders/Ugritone_Drums_KVLT_Mexico_1998     vst-test-Ugritone Drums (KVLT_Mexico 1998).txt    vst-kit-Ugritone Drums (KVLT_Mexico 1998).kit
  Riot       renders/Riot_Drums_saved_kit                vst-test-Riot Drums (saved kit).txt               vst-kit-Riot Drums (saved kit).kit

data: DrumMidi_CRT (the jar given, else Programs/Java's) on every MP3 (or WAV) with --no-cymbal-model
(the model learns to correct what DrumMidi writes without it), and for every cymbal it wrote: the
kind written, the kind played there (the MIDI, within 30 ms after the best shift; 0: none) and the
features, read in the song exactly as CymModel.feats reads them. <work>/<name>.npz.

check: leave one kit out (learned from the others, scored on it), then also the songs split in two
halves (learned on one half of the other kits' songs, scored on the other half of the kit's), at
margins 0, 0.2, 0.4 and 0.6: cymbals right added, by kind. All the kits play the same songs, so the
split shows the model learned the sounds, not the songs.

export: learned from every kit's hits; prints the Java arrays, and with --write puts them between
the "cymbal model: begin" and "end" markers in Programs/Java/DrumMidi_CRT.java (then rebuild the jar:
desktop/build.sh). Compare with bench first.

bench: DrumMidi with each kit's leave-one-out model (-Ddm.cymModelFile), scored as vst_render --test
does, beside DrumMidi with --no-cymbal-model: the model's gain where it learned nothing of the kit.

Needs numpy and ffmpeg (MP3s). The features must stay as CymModel.feats has them: change both.
"""
import glob
import importlib.util
import os
import re
import subprocess
import sys
import tempfile
import zlib
from multiprocessing import Pool

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "Programs", "Java", "DrumMidi_CRT.jar")
SOURCE = os.path.join(ROOT, "Programs", "Java", "DrumMidi_CRT.java")
_spec = importlib.util.spec_from_file_location("vr", os.path.join(ROOT, "Programs", "Python", "vst_render.py"))
vr = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(vr)

# Kinds: 1 closed hat (and pedal hat), 2 open hat, 3 ride (and bell), 4 crash (china, splash).
KIND = {42: 1, 44: 1, 46: 2, 51: 3, 53: 3, 59: 3, 49: 4, 52: 4, 55: 4, 57: 4}
KIND_NAMES = ("hat", "open", "ride", "crash")
SR = 44100
N = 1024
CONTEXT = 2.0
MARGINS = (0.0, 0.2, 0.4, 0.6)
FREQ = np.fft.rfftfreq(N, 1.0 / SR)
EDGES = 2000 * 8 ** (np.arange(13) / 12.0)
BANDS = [(FREQ >= EDGES[i]) & (FREQ < EDGES[i + 1]) for i in range(12)]
HI = (FREQ > 3000) & (FREQ < 12000)
WIN = np.hanning(N)
OFFS = (-0.03, 0.0, 0.03, 0.07, 0.12, 0.2)


# ---- kits.txt

def kits(path):
    """[(name, folder, report, kit profile or None)], paths relative to kits.txt's folder."""
    here = os.path.dirname(os.path.abspath(path))
    out = []
    for line in open(path, encoding="utf-8"):
        if not line.strip() or line.startswith("#"):
            continue
        w = [x.strip() for x in line.rstrip("\n").split("\t") if x.strip()]
        full = [os.path.join(here, x) for x in w[1:]]
        out.append((w[0], full[0], full[1], full[2] if len(full) > 2 else None))
    return out


def notes_map(report):
    """The --map the test played with, from its report ({note: note or None})."""
    m = re.search(r"Notes mapped \(--map[^)]*\): (.*)", open(report, encoding="utf-8", errors="replace").read())
    out = {}
    for pair in (m.group(1).split(",") if m else []):
        a, b = pair.strip().split("=")
        out[int(a)] = None if b == "none" else int(b)
    return out


def songs(folder):
    """[(audio, its MIDI)]: each render under mp3/ (or wav/) and the MIDI it was played from under midi/."""
    out = []
    for audio in sorted(glob.glob(os.path.join(folder, "mp3", "*", "*.mp3")) + glob.glob(os.path.join(folder, "wav", "*", "*.wav"))):
        sub = os.path.basename(os.path.dirname(audio))
        base = os.path.splitext(os.path.basename(audio))[0]
        mid = None
        # "<stem>-<Plugin> (<kit>)": the longest MIDI name the render's name starts with.
        for c in glob.glob(os.path.join(folder, "midi", sub, "*.mid")):
            s = os.path.basename(c)[:-4]
            if base.startswith(s + "-") and (mid is None or len(s) > len(os.path.basename(mid)) - 4):
                mid = c
        if mid:
            out.append((audio, mid))
    return out


# ---- the features (CymModel.feats)

def power(x, at):
    a = int(at * SR)
    if at < 0 or a + N > len(x):
        return None
    return np.abs(np.fft.rfft(x[a:a + N] * WIN)) ** 2


def feats(x, t, nxt):
    """A cymbal hit's features at `t` (the next hit at `nxt`): as CymModel.feats, F = 29 values."""
    ms = [power(x, t + d) for d in OFFS]
    if any(m is None for m in ms):
        return None
    b = [np.array([m[k].sum() for k in BANDS]) + 1e-12 for m in ms]
    e = [v.sum() for v in b]
    lw0 = np.log(np.maximum(b[1] - b[0], 0.05 * b[1]))
    shape = lw0 - lw0.mean()
    gap = nxt - t
    decay, missing = [], []
    for i, d in ((2, 0.03), (3, 0.07), (4, 0.12), (5, 0.2)):
        has = gap > d + 0.02
        decay.append(np.log(e[i] / e[1]) if has else 0.0)
        missing.append(0.0 if has else 1.0)

    def flat(m):
        v = m[HI] + 1e-12
        return np.exp(np.mean(np.log(v))) / np.mean(v)

    def peaky(m):
        v = m[HI]
        return np.log(np.max(v) / (np.median(v) + 1e-12))

    centroid = np.sum(FREQ * ms[1]) / np.sum(ms[1])
    l0 = np.log(b[1]) - np.log(b[1]).mean()
    l1 = np.log(b[2]) - np.log(b[2]).mean()
    same = float(np.dot(l0, l1) / (np.linalg.norm(l0) * np.linalg.norm(l1) + 1e-9))
    return np.concatenate([shape, decay, [np.log(e[1] / e[0]), flat(ms[1]), flat(ms[2]), peaky(ms[1]), peaky(ms[2]),
                                          np.log(centroid), same, np.log(min(gap, 2.0)), np.log(e[1])], missing])


def inputs(F, t, wrote, song):
    """The model's inputs (X = 63): the hit's features, the mean over the cymbals within CONTEXT s in its
    song, log(1 + how many), and the kind written (one-hot)."""
    C = np.zeros_like(F)
    n = np.zeros(len(F))
    for s in np.unique(song):
        idx = np.where(song == s)[0]
        tt = t[idx]
        for a, i in enumerate(idx):
            near = idx[(np.abs(tt - tt[a]) <= CONTEXT) & (idx != i)]
            if len(near):
                C[i] = F[near].mean(0)
                n[i] = len(near)
    return np.concatenate([F, C, np.log1p(n)[:, None], np.eye(5)[wrote][:, 1:]], 1)


# ---- data

def drum_midi(jar, audio, out, kit):
    argv = [audio, out, "--no-cymbal-model"] + (["--kit", kit] if kit else [])
    found = vr.drum_midi("java", jar, audio, out, argv[2:])
    return None if isinstance(found, str) else found


def one_song(job):
    name, audio, mid, nmap, kit, jar, work = job
    out = os.path.join(work, "dm", name, os.path.splitext(os.path.basename(audio))[0] + ".mid")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    found = drum_midi(jar, audio, out, kit)
    if found is None:
        return []
    raw = subprocess.run(["ffmpeg", "-v", "quiet", "-i", audio, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"], capture_output=True).stdout
    x = np.frombuffer(raw, "<i2").astype(np.float64) / 32768
    length, played = vr.midi_notes(mid)
    played = vr.mapped(played, nmap)
    _, shift = vr.score(played, found, length + 0.05)
    pc = sorted((t, KIND[n]) for t, n in played if n in KIND)
    ptimes = np.array([t for t, _ in pc])
    every = sorted(t for t, n in found)
    rows = []
    for t, n in sorted(found):
        if n not in KIND:
            continue
        nxt = min([u for u in every if u > t + 0.005] + [t + 2.0])
        f = feats(x, t, nxt)
        if f is None:
            continue
        lab = 0
        if len(ptimes):
            j = int(np.argmin(np.abs(ptimes - (t - shift))))
            if abs(ptimes[j] - (t - shift)) <= 0.03:
                lab = pc[j][1]
        rows.append((t, KIND[n], lab, f, os.path.basename(audio)))
    return rows


def data(kits_txt, work, jar=JAR):
    os.makedirs(work, exist_ok=True)
    for name, folder, report, kit in kits(kits_txt):
        nmap = notes_map(report)
        jobs = [(name, a, m, nmap, kit, jar, work) for a, m in songs(folder)]
        with Pool(min(8, os.cpu_count() or 1)) as p:
            res = p.map(one_song, jobs)
        rows = [r for song in res for r in song]
        if not rows:
            print("%s: no cymbals (%d songs)" % (name, len(jobs)))
            continue
        np.savez(os.path.join(work, name + ".npz"), t=np.array([r[0] for r in rows]), wrote=np.array([r[1] for r in rows]),
                 label=np.array([r[2] for r in rows]), f=np.array([r[3] for r in rows]), song=np.array([r[4] for r in rows]))
        w = np.array([r[1] for r in rows])
        lab = np.array([r[2] for r in rows])
        print("%s: %d songs, %d cymbals written, %d right; played there: %s, none %d" % (
            name, len(jobs), len(rows), np.sum(w == lab), ", ".join("%s %d" % (k, np.sum(lab == i + 1)) for i, k in enumerate(KIND_NAMES)), np.sum(lab == 0)))


# ---- learning

def load(work):
    """{name: (X, wrote, label, half)} for every <work>/*.npz; half: the song's half (its name without the kit)."""
    out = {}
    for p in sorted(glob.glob(os.path.join(work, "*.npz"))):
        d = np.load(p)
        X = inputs(d["f"], d["t"], d["wrote"], d["song"])
        stems = [re.sub(r"-[^-]*\([^)]*\)$", "", os.path.splitext(s)[0]) for s in d["song"]]
        half = np.array([zlib.crc32(s.encode()) % 2 for s in stems])
        out[os.path.basename(p)[:-4]] = (X, d["wrote"], d["label"], half)
    return out


def fit(X, y, reg=1e-2, it=400, lr=0.5):
    """Multinomial logistic regression (gradient descent) on standardized X; y in 1..4. (mu, sd, W, b)."""
    keep = y > 0
    X, y = X[keep], y[keep]
    mu, sd = X.mean(0), X.std(0) + 1e-9
    Z = (X - mu) / sd
    W = np.zeros((Z.shape[1], 4))
    b = np.zeros(4)
    Y = np.eye(4)[y - 1]
    for _ in range(it):
        z = Z @ W + b
        z -= z.max(1, keepdims=True)
        p = np.exp(z)
        p /= p.sum(1, keepdims=True)
        g = p - Y
        W -= lr * (Z.T @ g / len(Z) + reg * W)
        b -= lr * g.mean(0)
    return mu, sd, W, b


def relabel(model, X, wrote, margin):
    mu, sd, W, b = model
    z = ((X - mu) / sd) @ W + b
    z -= z.max(1, keepdims=True)
    p = np.exp(z)
    p /= p.sum(1, keepdims=True)
    best = p.argmax(1) + 1
    mine = p[np.arange(len(p)), wrote - 1]
    return np.where(p.max(1) - mine > margin, best, wrote)


def scores(model, X, wrote, label):
    base = np.sum(wrote == label)
    out = []
    for m in MARGINS:
        new = relabel(model, X, wrote, m)
        kinds = [int(np.sum((new == label) & (label == c)) - np.sum((wrote == label) & (label == c))) for c in (1, 2, 3, 4)]
        out.append((int(np.sum(new == label) - base), kinds))
    return base, out


def check(work):
    d = load(work)
    names = list(d)
    if len(names) < 2:
        sys.exit("check needs two kits or more in %s" % work)
    for title, halves in (("leave one kit out", None), ("and the songs split (learned on one half, scored on the other)", (0, 1))):
        print("== " + title)
        total = np.zeros(len(MARGINS), int)
        for k in names:
            line = []
            base_all = 0
            sums = np.zeros(len(MARGINS), int)
            for h in (halves or (None,)):
                tr = [d[o] for o in names if o != k]
                X = np.concatenate([t[0] for t in tr])
                y = np.concatenate([t[2] for t in tr])
                hv = np.concatenate([t[3] for t in tr])
                if h is not None:
                    y = np.where(hv == h, y, 0)
                Xk, wk, lk, hk = d[k]
                sel = np.ones(len(lk), bool) if h is None else hk != h
                base, res = scores(fit(X, y), Xk[sel], wk[sel], lk[sel])
                base_all += base
                sums += np.array([r[0] for r in res])
                if h is None:
                    line = ["m%.1f %+d (%s)" % (m, r[0], " ".join("%s %+d" % (n, v) for n, v in zip(KIND_NAMES, r[1]))) for m, r in zip(MARGINS, res)]
            if halves:
                line = ["m%.1f %+d" % (m, s) for m, s in zip(MARGINS, sums)]
            total += sums
            print("%-10s right now %5d | %s" % (k, base_all, " | ".join(line)))
        print("all: " + " ".join("m%.1f %+d" % (m, s) for m, s in zip(MARGINS, total)))


def java(model):
    mu, sd, W, b = model

    def arr(v):
        return "{" + ", ".join("%.6g" % a for a in v) + "}"
    return ("    static final double[] CM_MU = %s;\n    static final double[] CM_SD = %s;\n    static final double[] CM_B = %s;\n"
            "    static final double[][] CM_W = {%s};\n") % (arr(mu), arr(sd), arr(b), ", ".join(arr(r) for r in W))


def export(work, write=False):
    d = load(work)
    X = np.concatenate([t[0] for t in d.values()])
    y = np.concatenate([t[2] for t in d.values()])
    text = java(fit(X, y))
    if not write:
        print(text, end="")
        print("(learned from %s: %d hits; --write puts it in DrumMidi_CRT.java)" % (", ".join(d), int(np.sum(y > 0))))
        return
    src = open(SOURCE, encoding="utf-8").read()
    m = re.search(r"(        // cymbal model: begin\n)(.*?)(        // cymbal model: end\n)", src, re.S)
    if not m:
        sys.exit("No cymbal model markers in %s" % SOURCE)
    open(SOURCE, "w", encoding="utf-8").write(src[:m.start(2)] + text + src[m.end(2):])
    print("Wrote the model (from %s) into %s: rebuild with desktop/build.sh" % (", ".join(d), os.path.relpath(SOURCE, ROOT)))


def model_file(model, path):
    mu, sd, W, b = model
    with open(path, "w") as f:
        for key, v in (("mu", mu), ("sd", sd), ("b", b)):
            f.write(key + " " + " ".join("%.8g" % a for a in v) + "\n")
        for r in W:
            f.write("w " + " ".join("%.8g" % a for a in r) + "\n")


def bench_song(job):
    name, audio, mid, nmap, kit, jar, java_cmd = job
    out = tempfile.mktemp(suffix=".mid")
    argv = (["--kit", kit] if kit else []) + ([] if java_cmd != "java" else ["--no-cymbal-model"])
    found = vr.drum_midi(java_cmd, jar, audio, out, argv)
    if os.path.isfile(out):
        os.remove(out)
    if isinstance(found, str):
        return None
    length, played = vr.midi_notes(mid)
    played = vr.mapped(played, nmap)
    rows, shift = vr.score(played, found, length + 0.05)
    return rows


def bench(kits_txt, work, jar=JAR):
    d = load(work)
    for name, folder, report, kit in kits(kits_txt):
        if name not in d:
            print("%s: no data (run data first)" % name)
            continue
        others = [o for o in d if o != name]
        path = os.path.join(work, "without-%s.txt" % name)
        model_file(fit(np.concatenate([d[o][0] for o in others]), np.concatenate([d[o][2] for o in others])), path)
        nmap = notes_map(report)
        # Java with the kit's leave-one-out model in place of the built-in one.
        java_cmd = os.path.join(work, "java-without-%s.sh" % name)
        with open(java_cmd, "w") as f:
            f.write('#!/bin/sh\nexec java -Ddm.cymModelFile="%s" "$@"\n' % path)
        os.chmod(java_cmd, 0o755)
        for label, cmd in (("without the model", "java"), ("with the model", java_cmd)):
            jobs = [(name, a, m, nmap, kit, jar, cmd) for a, m in songs(folder)]
            with Pool(min(8, os.cpu_count() or 1)) as p:
                res = [r for r in p.map(bench_song, jobs) if r]
            fam = {}
            for rows in res:
                for f, t, n, ok in rows:
                    v = fam.setdefault(f, [0, 0, 0])
                    v[0] += t
                    v[1] += n
                    v[2] += ok
            print("%-10s %-18s %s" % (name, label, vr.score_line([(f, fam[f][0], fam[f][1], fam[f][2]) for f, _ in vr.FAMILIES if f in fam])))


if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) >= 3 and a[0] == "data":
        data(a[1], a[2], a[3] if len(a) > 3 else JAR)
    elif len(a) >= 2 and a[0] == "check":
        if len(a) > 2:
            MARGINS = (float(a[2]),)
        check(a[1])
    elif len(a) >= 2 and a[0] == "export":
        export(a[1], "--write" in a)
    elif len(a) >= 3 and a[0] == "bench":
        bench(a[1], a[2], a[3] if len(a) > 3 else JAR)
    else:
        print(__doc__)
        sys.exit(1)
