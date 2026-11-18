"""hit_model.py: DrumMidi's hit model (DrumMidi_CRT.HitModel): kicks, snares and toms, learned from kits
rendered from known MIDI. A small network (one hidden layer of 32) told each onset DrumMidi considers a kick,
snare or tom from its bands' energies around it and the rules' own say. The built-in one: 51 kits (5
DrumGizmo kits, also under a bass, guitars, a piano and a synthesizer (tools/accomp_mix.py); 46 VST kits),
548,000 onsets, and (18.11.2026) 15% of the onsets of the same 5 DrumGizmo kits under real piano and synth
recordings (SogniMusic's Piano only / Synth only tracks, accomp_mix.py layers:<folder>). Each kit held out: real
synth kick F 80.1 -> 80.6, snare 77.9 -> 79.5, tom 75.0 -> 76.0; real piano snare 83.9 -> 85.0; the other mixes
and kits as before (all of them at full weight: synth better still, but toms 1-2 down on most VST kits).
Since 18.11.2026 without the electronic kits (Beat DRMR, Drumatic): acoustic kits only. Each held out, against
the model with them: Ugritone kick +0.5, snare +0.2, tom +1.6; KVLT and Anarchy toms +2; DRSKit kick -2.4. With the piano and synth mixes (each kit held out): piano snare P86 R77 -> P89 R82, synth
kick P80 -> P90, snare R70 -> R77, tom P81 -> P89; the other mixes as before. Each plugin family held out
in turn (check): kick F 83 -> 93, snare 84 -> 94, tom 45 -> 85; all of DrumMidi on held-out songs: kick
P90 R80 -> P95 R93, snare P83 R83 -> P94 R89, toms P57 R36 -> P90 R74.

  python tools/hit_model.py data   <kits.txt> <work> [jar]   the training data, a .npz a kit
  python tools/hit_model.py check  <work>                    leave one kit family out: how it does on kits it never saw
  python tools/hit_model.py export <work> [--write] [--file <model.txt>]  the model from every kit; --write puts
                                                             it in DrumMidi_CRT.java, --file writes a model file
                                                             (DrumMidi -Ddm.hitModelFile=<file>)
  python tools/hit_model.py bench  <kits.txt> <work> [jar]   DrumMidi itself, each kit with a model learned without
                                                             it, scored as vst_render --test does, beside the rules

kits.txt: a kit a line, tab-separated: a short name ("family~kit": a plugin's kits, which share samples, are
held out together in check and bench) and a folder of renders: each <song>.wav (or .mp3) with
the MIDI played, <song>.mid beside it (kit_bench.py render's layout), or mp3/ and midi/ under it (vst-render
zips unzipped). Several folders of one kit (the kit with a bass over it, say) go on lines of the same name.
"#" lines are left out.

data: DrumMidi_CRT (the jar given, else Programs/Java's) with -Ddm.hitModel=0 (the rules alone) and
-Ddm.hitFeatures on every render: each onset that could be a kick, snare or tom and its features
(HitModel.feats), and what was played there (within 35 ms, after the best shift of DrumMidi's notes).

Needs numpy (and ffmpeg for MP3s only in the bench's scoring, as vst_render).
"""
import concurrent.futures as cf
import glob
import importlib.util
import os
import re
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
spec = importlib.util.spec_from_file_location("vr", os.path.join(ROOT, "Programs", "Python", "vst_render.py"))
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)

KICK, SNARE, TOMS = {35, 36}, {37, 38, 40}, {41, 43, 45, 47, 48, 50}
DRUMS = ("kick", "snare", "tom")
JAR = os.path.join(ROOT, "Programs", "Java", "DrumMidi_CRT.jar")
B = 12
F = 5 * B + 6
RULES = slice(5 * B, 5 * B + 3)


def kits(path):
    out = {}
    for line in open(path, encoding="utf-8"):
        if not line.strip() or line.startswith("#"):
            continue
        p = line.rstrip("\n").split("\t")
        out.setdefault(p[0].strip(), []).append(os.path.expanduser(p[1].strip()))
    return out


def songs(folder):
    """(audio, played MIDI) pairs in a folder."""
    out = []
    for a in sorted(glob.glob(os.path.join(folder, "*.wav")) + glob.glob(os.path.join(folder, "*.mp3"))):
        stem = os.path.splitext(os.path.basename(a))[0]
        if stem == "check" or stem.endswith("-dm"):
            continue
        for m in (os.path.join(folder, stem + ".mid"), os.path.join(folder, "midi", stem + ".mid")):
            if os.path.isfile(m):
                out.append((a, m))
                break
    # vst-render zips: mp3/<run>/<song>-<kit>.mp3 played from midi/<run>/<song>.mid.
    mids = {}
    for m in glob.glob(os.path.join(folder, "midi", "**", "*.mid"), recursive=True):
        mids.setdefault(os.path.relpath(os.path.dirname(m), os.path.join(folder, "midi")), []).append(m)
    for a in sorted(glob.glob(os.path.join(folder, "mp3", "**", "*.mp3"), recursive=True)):
        stem = os.path.splitext(os.path.basename(a))[0]
        run = os.path.relpath(os.path.dirname(a), os.path.join(folder, "mp3"))
        best = None
        for m in mids.get(run, []):
            ms = os.path.splitext(os.path.basename(m))[0]
            if stem.startswith(ms + "-") and (best is None or len(ms) > len(os.path.splitext(os.path.basename(best))[0])):
                best = m
        if best:
            out.append((a, best))
    return out


def one(jar, audio, mid, extra=()):
    """DrumMidi's onsets' features and labels for a render: (X, Y)."""
    with tempfile.TemporaryDirectory() as tmp:
        feat = os.path.join(tmp, "f.txt")
        out = os.path.join(tmp, "o.mid")
        subprocess.run(["java", "-Ddm.hitModel=0", "-Ddm.hitFeatures=" + feat, "-cp", jar, "DrumMidi_CRT", os.path.abspath(audio), "o.mid"] + list(extra),
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, cwd=tmp)
        if not os.path.isfile(feat) or not os.path.isfile(out):
            return None
        length, played = vr.midi_notes(mid)
        found = vr.midi_notes(out)[1]
        _, shift = vr.score(played, found, length + 0.05)
        rows = [list(map(float, l.split())) for l in open(feat) if l.strip()]
    if not rows:
        return None
    a = np.array(rows)
    t = a[:, 0]
    X = a[:, 1:]
    Y = np.zeros((len(t), 3))
    pt = [(tt + shift, n) for tt, n in played]
    for i, x in enumerate(t):
        for tt, n in pt:
            if abs(tt - x) <= 0.035:
                if n in KICK:
                    Y[i, 0] = 1
                elif n in SNARE:
                    Y[i, 1] = 1
                elif n in TOMS:
                    Y[i, 2] = 1
    return X, Y, t


def data(kits_txt, work, jar):
    os.makedirs(work, exist_ok=True)
    for name, folders in kits(kits_txt).items():
        dest = os.path.join(work, name + ".npz")
        if os.path.isfile(dest):
            print(name + ": there already", flush=True)
            continue
        pairs = [p for f in folders for p in songs(f)]
        Xs, Ys, S = [], [], []
        with cf.ThreadPoolExecutor(4) as ex:
            for k, r in enumerate(ex.map(lambda p: one(jar, *p), pairs)):
                if r is None:
                    continue
                Xs.append(r[0])
                Ys.append(r[1])
                S.append(np.full(len(r[0]), k))
        np.savez(dest, X=np.vstack(Xs), Y=np.vstack(Ys), S=np.concatenate(S))
        print("%s: %d songs, %d onsets" % (name, len(Xs), sum(len(x) for x in Xs)), flush=True)


def load(work):
    return {os.path.splitext(os.path.basename(f))[0]: dict(np.load(f)) for f in sorted(glob.glob(os.path.join(work, "*.npz")))}


def fit(X, Y, hidden=32, epochs=6, seed=0, lr=2e-3):
    """A small network: one hidden layer of ReLU units, three sigmoid outputs (kick, snare, tom), trained with Adam."""
    r = np.random.default_rng(seed)
    mu = X.mean(0)
    sd = X.std(0) + 1e-6
    Z = (X - mu) / sd
    W1 = r.normal(0, 1 / np.sqrt(Z.shape[1]), (Z.shape[1], hidden))
    b1 = np.zeros(hidden)
    W2 = r.normal(0, 1 / np.sqrt(hidden), (hidden, 3))
    b2 = np.zeros(3)
    ps = [W1, b1, W2, b2]
    m1 = [np.zeros_like(p) for p in ps]
    m2 = [np.zeros_like(p) for p in ps]
    t = 0
    for _ in range(epochs):
        idx = r.permutation(len(Z))
        for s in range(0, len(Z), 512):
            i = idx[s:s + 512]
            z, y = Z[i], Y[i]
            a = z @ W1 + b1
            h = np.maximum(a, 0)
            P = 1 / (1 + np.exp(-(h @ W2 + b2)))
            g = (P - y) / len(i)
            gh = g @ W2.T * (a > 0)
            grads = [z.T @ gh + 1e-4 * W1, gh.sum(0), h.T @ g, g.sum(0)]
            t += 1
            for k, (p, gr) in enumerate(zip(ps, grads)):
                m1[k] = 0.9 * m1[k] + 0.1 * gr
                m2[k] = 0.999 * m2[k] + 0.001 * gr * gr
                p -= lr * (m1[k] / (1 - 0.9 ** t)) / (np.sqrt(m2[k] / (1 - 0.999 ** t)) + 1e-8)
    return mu, sd, W1, b1, W2, b2


def predict(m, X):
    mu, sd, W1, b1, W2, b2 = m
    return 1 / (1 + np.exp(-(np.maximum(((X - mu) / sd) @ W1 + b1, 0) @ W2 + b2)))


def model_text(m, th):
    """The model as DrumMidi reads it with -Ddm.hitModelFile."""
    mu, sd, W1, b1, W2, b2 = m
    row = lambda key, v: key + " " + " ".join("%.6g" % x for x in v) + "\n"
    return (row("mu", mu) + row("sd", sd) + row("t", th) + row("b1", b1) + row("b2", b2)
            + "".join(row("w1", r) for r in W1) + "".join(row("w2", r) for r in W2))


def thresholds(P, Y):
    """For each drum, the cut with the best F on the training set."""
    out = []
    for k in range(3):
        best, bt = -1, 0.5
        for t in np.arange(0.2, 0.81, 0.05):
            tp = ((P[:, k] >= t) & (Y[:, k] > 0)).sum()
            fp = ((P[:, k] >= t) & (Y[:, k] == 0)).sum()
            fn = ((P[:, k] < t) & (Y[:, k] > 0)).sum()
            f = 2 * tp / max(1, 2 * tp + fp + fn)
            if f > best:
                best, bt = f, t
        out.append(bt)
    return np.array(out)


def f1(pred, y):
    tp = (pred & (y > 0)).sum()
    fp = (pred & (y == 0)).sum()
    fn = (~pred & (y > 0)).sum()
    return 100.0 * 2 * tp / max(1, 2 * tp + fp + fn), int(tp), int(fp), int(fn)


def family(name):
    """A kit's family: the part of its name before "~" (kits of one plugin share samples: held out together)."""
    return name.split("~")[0]


def check(work):
    d = load(work)
    names = list(d)
    tot = {k: [np.zeros(4), np.zeros(4)] for k in DRUMS}
    for held in sorted({family(n) for n in names}):
        tr = [n for n in names if family(n) != held]
        X = np.vstack([d[n]["X"] for n in tr])
        Y = np.vstack([d[n]["Y"] for n in tr])
        m = fit(X, Y)
        th = thresholds(predict(m, X), Y)
        Xh = np.vstack([d[n]["X"] for n in names if family(n) == held])
        Yh = np.vstack([d[n]["Y"] for n in names if family(n) == held])
        P = predict(m, Xh)
        line = "%-16s" % held
        for k, dr in enumerate(DRUMS):
            rule = Xh[:, 5 * B + k] > 0.5
            fr = f1(rule, Yh[:, k])
            fm = f1(P[:, k] >= th[k], Yh[:, k])
            tot[dr][0] += fr[1:] + (0,)
            tot[dr][1] += fm[1:] + (0,)
            line += "  %s rules %5.1f model %5.1f" % (dr, fr[0], fm[0])
        print(line, flush=True)
    for dr in DRUMS:
        r, m = tot[dr]
        print("All %-5s rules F %5.1f -> model F %5.1f (the onsets DrumMidi considers)" % (dr, 200 * r[0] / max(1, 2 * r[0] + r[1] + r[2]), 200 * m[0] / max(1, 2 * m[0] + m[1] + m[2])))


def export(work, write, file):
    d = load(work)
    X = np.vstack([v["X"] for v in d.values()])
    Y = np.vstack([v["Y"] for v in d.values()])
    m = fit(X, Y)
    th = thresholds(predict(m, X), Y)
    mu, sd, W1, b1, W2, b2 = m
    if file:
        with open(file, "w") as f:
            f.write(model_text(m, th))
        print("Wrote " + file)
    arr = lambda v: "{" + ", ".join("%.6g" % x for x in v) + "}"
    java = ("        static double[] HM_MU = %s, HM_SD = %s;\n        static double[][] HM_W = null;\n        static double[] HM_B = null, HM_T = %s;\n"
            "        static double[][] HM_W1 = {%s}, HM_W2 = {%s};\n        static double[] HM_B1 = %s, HM_B2 = %s;\n"
            % (arr(mu), arr(sd), arr(th), ", ".join(arr(r) for r in W1), ", ".join(arr(r) for r in W2), arr(b1), arr(b2)))
    if write:
        p = os.path.join(ROOT, "Programs", "Java", "DrumMidi_CRT.java")
        s = open(p, encoding="utf-8").read()
        a = s.index("        // hit model: begin\n") + len("        // hit model: begin\n")
        e = s.index("        // hit model: end\n")
        open(p, "w", encoding="utf-8").write(s[:a] + java + s[e:])
        print("Wrote the model into " + p + " (rebuild the jar: python tools/make_drummidi_jar.py)")
    else:
        print(java)


def bench(kits_txt, work, jar):
    """DrumMidi on every kit's renders: the rules alone, and with a model learned without that kit."""
    d = load(work)
    ks = kits(kits_txt)
    tot = {"rules": {}, "model": {}}
    for name, folders in ks.items():
        others = [n for n in d if family(n) != family(name)]
        if not others:
            continue
        X = np.vstack([d[n]["X"] for n in others])
        Y = np.vstack([d[n]["Y"] for n in others])
        m = fit(X, Y)
        th = thresholds(predict(m, X), Y)
        fd, mfile = tempfile.mkstemp(suffix=".txt")
        os.close(fd)
        with open(mfile, "w") as f:
            f.write(model_text(m, th))
        pairs = [p for f in folders for p in songs(f)]
        for way, opts in (("rules", ["-Ddm.hitModel=0"]), ("model", ["-Ddm.hitModelFile=" + mfile])):
            fam = {}

            def run(p):
                audio, mid = p
                with tempfile.TemporaryDirectory() as tmp:
                    subprocess.run(["java"] + opts + ["-cp", jar, "DrumMidi_CRT", os.path.abspath(audio), "o.mid"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, cwd=tmp)
                    o = os.path.join(tmp, "o.mid")
                    if not os.path.isfile(o):
                        return None
                    length, played = vr.midi_notes(mid)
                    return vr.score(played, vr.midi_notes(o)[1], length + 0.05)[0]

            with cf.ThreadPoolExecutor(4) as ex:
                for rows in ex.map(run, pairs):
                    for f, t, n, ok in rows or []:
                        v = fam.setdefault(f, [0, 0, 0])
                        v[0] += t
                        v[1] += n
                        v[2] += ok
            for f, v in fam.items():
                w = tot[way].setdefault(f, [0, 0, 0])
                for i in range(3):
                    w[i] += v[i]
            print("%-16s %-5s %s" % (name, way, vr.score_line([(f, fam[f][0], fam[f][1], fam[f][2]) for f, _ in vr.FAMILIES if f in fam])), flush=True)
        os.remove(mfile)
    for way in ("rules", "model"):
        fam = tot[way]
        print("All, %s: %s" % (way, vr.score_line([(f, fam[f][0], fam[f][1], fam[f][2]) for f, _ in vr.FAMILIES if f in fam])))


if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) >= 3 and a[0] == "data":
        data(a[1], a[2], a[3] if len(a) > 3 else JAR)
    elif len(a) >= 2 and a[0] == "check":
        check(a[1])
    elif len(a) >= 2 and a[0] == "export":
        export(a[1], "--write" in a, a[a.index("--file") + 1] if "--file" in a else None)
    elif len(a) >= 3 and a[0] == "bench":
        bench(a[1], a[2], a[3] if len(a) > 3 else JAR)
    else:
        sys.exit(__doc__)
