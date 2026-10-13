"""drumgizmo_render.py <kit.xml> <midimap.xml> <in.mid> <out.wav> [full|close|room] [seed]
DrumMidi test renders (Pulsekit's tools): a free DrumGizmo kit (drumgizmo.org/kits) played from a MIDI file.
Notes the kit's map lacks are not played: for a test, move them first (drumgizmo_remap.py).
A DrumGizmo kit played from a MIDI file with exact timing: each hit takes the sample whose strength is
nearest its velocity (a random one of the nearest few, as a drummer's hits differ), every mic channel of
it; hats in one group choke each other (10 ms fade). Mics mixed to stereo (names ending L/R panned)."""
import os, re, sys, struct, random
import xml.etree.ElementTree as ET
import numpy as np

kitf, mapf, midf, out = sys.argv[1:5]
mode = sys.argv[5] if len(sys.argv) > 5 else "full"
rng = random.Random(int(sys.argv[6]) if len(sys.argv) > 6 else 1)
base = os.path.dirname(os.path.abspath(kitf))
SR = 44100


from collections import OrderedDict
CACHE = OrderedDict()
BUDGET = [0]


def wav(path):
    # Least recently used samples go once the cache holds about 2 GB (a big kit's songs need more).
    if path in CACHE:
        CACHE.move_to_end(path)
        return CACHE[path]
    a = load(path)
    CACHE[path] = a
    BUDGET[0] += a.nbytes
    while BUDGET[0] > 2e9 and len(CACHE) > 1:
        _, old = CACHE.popitem(last=False)
        BUDGET[0] -= old.nbytes
    return a


def load(path):
    d = open(path, "rb").read()
    p, fmt, data = 12, None, None
    while p + 8 <= len(d):
        cid, size = d[p:p + 4], struct.unpack("<I", d[p + 4:p + 8])[0]
        if cid == b"fmt ":
            fmt = struct.unpack("<HHIIHH", d[p + 8:p + 24])
        if cid == b"data":
            data = d[p + 8:p + 8 + size]
        p += 8 + size + (size & 1)
    tag, ch, sr, _, _, bits = fmt
    if tag == 3:
        a = np.frombuffer(data, dtype="<f4")
    elif bits == 16:
        a = np.frombuffer(data, dtype="<i2") / 32768.0
    elif bits == 24:
        b = np.frombuffer(data[:len(data) // 3 * 3], dtype=np.uint8).reshape(-1, 3).astype(np.int32)
        v = b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16)
        a = np.where(v >= 1 << 23, v - (1 << 24), v) / float(1 << 23)
    else:
        a = np.frombuffer(data, dtype="<i4") / 2.0 ** 31
    a = a[:len(a) // ch * ch].reshape(-1, ch).astype(np.float32)
    if sr != SR:
        x = np.linspace(0, len(a) - 1, int(len(a) * SR / sr))
        a = np.stack([np.interp(x, np.arange(len(a)), a[:, c]) for c in range(ch)], axis=1).astype(np.float32)
    return a


kit = ET.parse(kitf).getroot()
channels = [c.get("name") for c in kit.find("channels")]
instruments = {}
for ins in kit.find("instruments"):
    name = ins.get("name")
    cmap = {m.get("in"): m.get("out") for m in ins.findall("channelmap")}
    main = [m.get("in") for m in ins.findall("channelmap") if m.get("main") == "true"]
    instruments[name] = {"file": os.path.join(base, ins.get("file")), "group": ins.get("group"), "map": cmap, "main": main, "samples": None}


def samples(name):
    ins = instruments[name]
    if ins["samples"] is None:
        root = ET.parse(ins["file"]).getroot()
        folder = os.path.dirname(ins["file"])
        out = []
        for s in root.iter("sample"):
            files = []
            for af in s.findall("audiofile"):
                files.append((af.get("channel"), os.path.join(folder, af.get("file")), int(af.get("filechannel", "1")) - 1))
            power = s.get("power")
            out.append({"files": files, "power": float(power) if power else None})
        # Strength of samples without a power: the main mics' peak in the first 30 ms.
        for s in out:
            if s["power"] is None:
                m = [f for f in s["files"] if f[0] in ins["main"]] or s["files"]
                s["power"] = max(float(np.max(np.abs(wav(p)[:int(0.03 * SR), c]))) for _, p, c in m)
        out.sort(key=lambda s: s["power"])
        ins["samples"] = out
    return ins["samples"]


def vl(d, p):
    v = 0
    while True:
        b = d[p]; p += 1; v = (v << 7) | (b & 0x7F)
        if b < 0x80: return v, p
def midi_events(path):
    """(length s, [(seconds, note, velocity)]) of a MIDI file, tempo map followed."""
    d = open(path, "rb").read()
    ppq = struct.unpack(">H", d[12:14])[0] or 480
    i = 8 + struct.unpack(">I", d[4:8])[0]
    tempos, ons, last = [], [], 0
    while i + 8 <= len(d):
        kind, size = d[i:i+4], struct.unpack(">I", d[i+4:i+8])[0]
        p, end, tick, status = i + 8, i + 8 + size, 0, 0
        while kind == b"MTrk" and p < end:
            dt, p = vl(d, p); tick += dt; b = d[p]
            if b == 0xFF:
                t = d[p+1]; n, p = vl(d, p + 2)
                if t == 0x51: tempos.append((tick, (d[p] << 16) | (d[p+1] << 8) | d[p+2]))
                p += n; last = max(last, tick); continue
            if b in (0xF0, 0xF7):
                n, p = vl(d, p + 1); p += n; continue
            if b & 0x80: status = b; p += 1
            hi = status & 0xF0
            if hi in (0xC0, 0xD0): p += 1; continue
            if hi == 0x90 and d[p+1] > 0: ons.append((tick, d[p], d[p+1]))
            p += 2; last = max(last, tick)
        i += 8 + size
    tempos.sort()
    def sec(tk):
        s, at, us = 0.0, 0, 500000
        for t, v in tempos:
            if t >= tk: break
            s += (t - at) * us / 1e6 / ppq; at, us = t, v
        return s + (tk - at) * us / 1e6 / ppq
    return sec(last), sorted((sec(t), n, v) for t, n, v in ons)


notemap = {}
for m in ET.parse(mapf).getroot().iter("map"):
    notemap.setdefault(int(m.get("note")), m.get("instr"))


length, hits = midi_events(midf)
n = int((length + 3) * SR)
mix = {c: np.zeros(n, dtype=np.float32) for c in channels}
# First the hits' samples and their choke points (another hat of the group cuts what rings), then
# each is added straight into the mix (no copies kept: a big kit's song would not fit in memory).
voices = []  # [group, start, length, cut, sample, gain, instrument]
for t, note, vel in hits:
    name = notemap.get(note)
    if name is None or name not in instruments:
        continue
    ss = samples(name)
    top = ss[-1]["power"]
    want = top * (vel / 127.0)
    order = sorted(range(len(ss)), key=lambda i: abs(ss[i]["power"] - want))
    pick = ss[rng.choice(order[:min(3, len(order))])]
    # A louder sample than asked for is turned down to the velocity (ghost notes below the softest).
    gain = min(1.0, want / pick["power"]) if pick["power"] > 0 else 1.0
    start = int(round(t * SR))
    length_ = max(len(wav(p)) for _, p, c in pick["files"])
    group = instruments[name]["group"]
    if group:
        for v in voices:
            if v[0] == group and v[1] + v[2] > start and v[3] is None:
                v[3] = start
    voices.append([group, start, length_, None, pick, gain, name])
fade_n = int(0.01 * SR)
for group, start, length_, cut, pick, gain, name in voices:
    for ch, path, c in pick["files"]:
        outch = instruments[name]["map"].get(ch, ch)
        if outch not in mix:
            continue
        a = wav(path)[:, c] * gain
        if cut is not None and cut - start < len(a):
            k = max(0, cut - start)
            f = min(len(a) - k, fade_n)
            a[k:k + f] *= np.linspace(1, 0, f, dtype=np.float32)
            a = a[:k + f]
        e = min(n, start + len(a))
        if e > start:
            mix[outch][start:e] += a[:e - start]

L = np.zeros(n, dtype=np.float64)
R = np.zeros(n, dtype=np.float64)
for c in channels:
    low = c.lower()
    amb = re.search(r"amb|room|oh|over", low) is not None
    if "trigger" in low or mode == "close" and amb or mode == "room" and not amb:
        continue
    # Overhead and room pairs are panned (OH-L, OHL, AmbR); close mics are in the middle.
    if amb and re.search(r"(l|left)$", low):
        L += mix[c]
    elif amb and re.search(r"(r|right)$", low):
        R += mix[c]
    else:
        L += mix[c] * 0.7
        R += mix[c] * 0.7
st = np.vstack([L, R])
st = st / (np.max(np.abs(st)) or 1) * 0.89
import wave
w = wave.open(out, "wb")
w.setnchannels(2)
w.setsampwidth(2)
w.setframerate(SR)
w.writeframes(np.round(st.T * 32767).astype("<i2").tobytes())
w.close()
print("%s: %d hits, %d channels (%s), %.1f s" % (os.path.basename(out), len(hits), len(channels), mode, n / SR))
