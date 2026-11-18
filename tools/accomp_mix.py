"""accomp_mix.py <bench out> <dest> <bass|guitar|clean|both|piano|synth|layers:<folder>> [level]: kit_bench.py renders with a synthetic bass /
distorted guitar / clean strummed guitar / acoustic piano (chords and a melody, a left-hand bass note) / synthesizer (a held
pad, a plucked arpeggio, a synth bass in half the songs) over them (as loud as the drums, x level), for scoring DrumMidi on
drums in a band (kit_bench.py score <dest>).
layers:<folder>: real recordings instead (SogniMusic's Piano only / Synth only tracks, MP3 or WAV): for each song one
drawn at random, from a random point, looped to the song's length (needs ffmpeg for MP3s)."""
import glob, os, re, shutil, sys, wave
import numpy as np
src, dest, kind = sys.argv[1], sys.argv[2], sys.argv[3]
level = float(sys.argv[4]) if len(sys.argv) > 4 else 1.0

def read(p):
    w = wave.open(p); n = w.getnframes(); ch = w.getnchannels(); sr = w.getframerate()
    a = np.frombuffer(w.readframes(n), dtype='<i2').astype(np.float64).reshape(-1, ch) / 32768.0
    return a, sr

def write(p, a, sr):
    a = np.clip(a, -1, 1)
    w = wave.open(p, 'wb'); w.setnchannels(a.shape[1]); w.setsampwidth(2); w.setframerate(sr)
    w.writeframes((a * 32767).astype('<i2').tobytes()); w.close()

def note(f, dur, sr, harm, decay, bright_decay, attack=0.004, dist=0.0):
    t = np.arange(int(dur * sr)) / sr
    out = np.zeros_like(t)
    for k in range(1, harm + 1):
        if f * k > sr / 2.2: break
        out += np.sin(2 * np.pi * f * k * t + k) / k * np.exp(-t * (decay + bright_decay * (k - 1)))
    env = np.minimum(1, t / attack) * np.exp(-t * decay * 0.2)
    out *= env
    rel = int(min(len(t), 0.01 * sr)); out[-rel:] *= np.linspace(1, 0, rel)
    if dist: out = np.tanh(out * dist) / np.tanh(dist)
    return out

def part(kind, n, sr, bpm, rng):
    beat = 60.0 / bpm; out = np.zeros(n)
    synth_bass = kind == 'synth' and rng.random() < 0.5
    root = rng.choice([41.2, 43.65, 46.25, 49.0, 55.0, 58.27, 61.74])  # E1..B1
    prog = [1, 1, 4 / 3.0, 3 / 2.0, 6 / 5.0, 9 / 8.0]
    t = 0.0; bar = 0
    while t < n / sr:
        r = root * prog[rng.integers(len(prog))]
        for step in range(8):  # eighths in a bar
            at = t + step * beat / 2
            if at >= n / sr: break
            if kind == 'bass':
                if rng.random() < 0.25: continue
                d = beat / 2 * (2 if rng.random() < 0.3 else 1)
                v = note(r * (2 if rng.random() < 0.15 else 1), d, sr, 14, 3.0, 1.2, 0.003)
                v *= 0.9 + 0.2 * rng.random()
            elif kind == 'guitar':  # distorted power chords, eighths, some palm mutes
                f = r * 2
                mute = rng.random() < 0.4
                d = beat / 2 if not mute else beat / 4
                v = sum(note(f * m, d, sr, 30, 1.5 if not mute else 12, 0.15, 0.002) for m in (1, 1.5, 2))
                v = np.tanh(v * 6) * (0.6 if mute else 1.0)
            elif kind == 'piano':  # chords on the beats (and some offbeats), a melody note above, a low bass note on 1 and 3
                if step % 2 and rng.random() < 0.55: continue
                d = beat * (1.0 if step % 2 == 0 else 0.5)
                v = np.zeros(int(d * sr))
                for m in (4, 5, 6) if rng.random() < 0.5 else (4, 5, 6, 8):
                    v += 0.5 * note(r * m, d, sr, 10, 2.5, 0.9, 0.002)
                if rng.random() < 0.6:
                    v += 0.6 * note(r * rng.choice([8, 9, 10, 12, 16]), d, sr, 8, 3.0, 1.2, 0.002)
                if step in (0, 4):
                    v += 0.5 * note(r * 2, d, sr, 12, 1.8, 0.6, 0.003)
            elif kind == 'synth':  # a held saw pad each bar, a plucked arpeggio in sixteenths, a synth bass (synth_bass)
                v = np.zeros(int(beat / 2 * sr))
                if step == 0:
                    pad = sum(note(r * m, beat * 4, sr, 25, 0.25, 0.08, 0.12) for m in (4, 5, 6))
                    v = np.zeros(len(pad)); v += 0.35 * pad
                for half in (0, 1):
                    if rng.random() < 0.3: continue
                    pl = 0.5 * note(r * rng.choice([8, 10, 12, 16]), beat / 4, sr, 20, 9.0, 2.5, 0.002)
                    o = int(half * beat / 4 * sr); v[o:o + len(pl)] += pl[: max(0, len(v) - o)]
                if synth_bass and rng.random() < 0.8:
                    sb = 0.9 * note(r, beat / 2 * 0.9, sr, 20, 4.0, 3.0, 0.002)
                    v[:len(sb)] += sb[:len(v)]
            else:  # clean strummed chords: on beats, six strings a few ms apart
                if step % 2 and rng.random() < 0.6: continue
                f = r * 2; d = beat * 0.95
                v = np.zeros(int(d * sr) + int(0.04 * sr))
                for i, m in enumerate((1, 1.5, 2, 2.5, 3, 4)):
                    s = note(f * m, d, sr, 12, 2.0, 0.6, 0.002); o = int(i * 0.006 * sr)
                    v[o:o + len(s)] += s
            a = int(at * sr); v = v[: n - a]
            out[a:a + len(v)] += v
        t += beat * 4; bar += 1
    return out

rng = np.random.default_rng(7)
LAYERS = None
if kind.startswith('layers:'):
    import subprocess
    LAYERS = sorted(glob.glob(os.path.join(kind[7:], '*.mp3')) + glob.glob(os.path.join(kind[7:], '*.wav')))
    if not LAYERS: sys.exit('no MP3 or WAV files in ' + kind[7:])
    CACHE = {}

def layer(n, sr):
    """A real recording, drawn at random, from a random point, looped to n samples."""
    f = LAYERS[rng.integers(len(LAYERS))]
    key = (f, sr)
    if key not in CACHE:
        raw = subprocess.run(['ffmpeg', '-v', 'quiet', '-i', f, '-ac', '1', '-ar', str(sr), '-f', 's16le', '-'], stdout=subprocess.PIPE, check=True).stdout
        CACHE[key] = np.frombuffer(raw, dtype='<i2').astype(np.float64) / 32768.0
    x = CACHE[key]
    if len(x) < sr: return np.zeros(n)
    start = int(rng.integers(len(x)))
    reps = int(np.ceil((n + start) / len(x))) + 1
    return np.tile(x, reps)[start:start + n]
for kitdir in sorted(d for d in glob.glob(os.path.join(src, '*')) if os.path.isdir(d)):
    to = os.path.join(dest, os.path.basename(kitdir)); os.makedirs(to, exist_ok=True)
    for f in glob.glob(os.path.join(kitdir, '*')):
        b = os.path.basename(f)
        if b.endswith('-dm.mid') or os.path.isdir(f): continue
        if not b.endswith('.wav') or b == 'check.wav':
            shutil.copy(f, to); continue
        m = re.search(r'-(\d+)bpm', b); bpm = float(m.group(1)) if m else 120.0
        a, sr = read(f)
        kinds = ['bass', 'guitar'] if kind == 'both' else [kind]
        acc = layer(len(a), sr) if LAYERS else sum(part(k, len(a), sr, bpm, rng) for k in kinds)
        drms = np.sqrt(np.mean(a ** 2)) + 1e-9; arms = np.sqrt(np.mean(acc ** 2)) + 1e-9
        acc *= drms / arms * level  # as loud as the drums (x level)
        mix = a + acc[:, None]
        pk = np.abs(mix).max()
        write(os.path.join(to, b), mix / max(1.0, pk / 0.95), sr)
print('done', dest)
