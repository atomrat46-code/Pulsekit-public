# DrumMidi with a kit whose kick has no beater click (an 808's sine, Beat DRMR's) and with one whose
# kick clicks: --learn-kit writes "kickclick" for each; with the 808 kit's profile its kicks are found
# without a click; the clicking kit is found as before. Used by DesktopBehavior s245.
# Arguments: vst_render.py, DrumMidi_CRT.jar, a work folder.
import importlib.util
import os
import subprocess
import sys
import types
import wave

import numpy as np

sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')
spec = importlib.util.spec_from_file_location('vr', sys.argv[1])
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)
jar, work = os.path.abspath(sys.argv[2]), sys.argv[3]
os.chdir(work)
R = 44100
rng = np.random.default_rng(5)


def env(n, tau):
    return np.exp(-np.arange(n) / (tau * R))


def sound(kind):
    n = int(1.0 * R)
    t = np.arange(n) / R
    if kind == '808':
        # A sine sweeping down to 54 Hz, ringing long: no click at all.
        return 0.9 * np.sin(2 * np.pi * (54 * t + 30 * (1 - np.exp(-t * 40)) / 40)) * env(n, 0.35)
    if kind == 'kick':
        # A kick with its beater click (2-6 kHz noise for 4 ms).
        body = 0.9 * np.sin(2 * np.pi * (55 * t + 80 * (1 - np.exp(-t * 30)) / 30)) * env(n, 0.12)
        click = np.zeros(n)
        click[:400] = np.sin(2 * np.pi * 3500 * t[:400]) * np.exp(-np.arange(400) / 120.0) * 0.8
        return body + click
    if kind == 'tom':
        return 0.8 * np.sin(2 * np.pi * 140 * t) * env(n, 0.15)
    if kind == 'snare':
        return (0.5 * rng.standard_normal(n) + 0.4 * np.sin(2 * np.pi * 190 * t)) * env(n, 0.07)
    if kind == 'hat':
        return 0.3 * np.diff(np.diff(rng.standard_normal(n), prepend=0), prepend=0) * env(n, 0.02)


def render(notes, kick, seconds):
    seconds = max([seconds] + [t + 1.5 for t, n in notes])
    out = np.zeros(int(seconds * R) + R)
    for t, n in notes:
        kind = kick if n == 36 else 'tom' if n in (41, 43, 45, 47, 48, 50) else 'snare' if n == 38 else 'hat' if n in (42, 46, 51, 49) else None
        if kind is None:
            continue
        x = sound(kind)
        a = int(t * R)
        out[a:a + len(x)] += x[:len(out) - a]
    return out / max(1e-9, np.abs(out).max()) * 0.8


def write(path, x):
    w = wave.open(path, 'wb')
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(R)
    w.writeframes((x * 32767).astype('<i2').tobytes())
    w.close()


open('times.txt', 'w').write(''.join(vr.kit_times_text({})))
beat = [(t, n) for t, n, _, tag in vr.kit_events() if tag in ('', 'k', 's', 'g')]
# A song: kicks on the beats and the offbeats, a snare on 2 and 4, hats on the eighths, 16 bars at 120 BPM.
song = []
for bar in range(16):
    for e in range(8):
        t = 0.5 + bar * 2.0 + e * 0.25
        # (hats only between the kicks: a hat's noise on a kick would pass for its click)
        if e not in (0, 3, 4, 7):
            song.append((t, 42))
        if e in (0, 3, 4, 7):
            song.append((t, 36))
        if e in (2, 6):
            song.append((t, 38))
kicks = [t for t, n in song if n == 36]
for kick in ('808', 'kick'):
    write('beat.wav', render(beat, kick, 40))
    r = subprocess.run(['java', '-cp', jar, 'DrumMidi_CRT', '--learn-kit', 'beat.wav', 'times.txt', kick + '.kit'], capture_output=True, text=True)
    line = [l for l in open(kick + '.kit') if l.startswith('kickclick')] if os.path.isfile(kick + '.kit') else []
    click = float(line[0].split()[1]) if line else -1
    write('song.wav', render(song, kick, 34))
    r = subprocess.run(['java', '-cp', jar, 'DrumMidi_CRT', 'song.wav', 'song.mid', '--kit', kick + '.kit'], capture_output=True, text=True)
    said = [l for l in r.stdout.splitlines() if 'beater click' in l]
    found = [t for t, n in vr.midi_notes('song.mid')[1] if n == 36] if os.path.isfile('song.mid') else []
    hit = sum(1 for t in kicks if any(abs(u - t) < 0.05 for u in found))
    print('%s: kickclick %s, %s, kicks %d of %d found, %d written' % (kick, 'under' if 0 <= click < 0.0001 else 'over' if click >= 0.0001 else 'none',
                                                                    'clickless' if said else 'clicks', hit, len(kicks), len(found)))
