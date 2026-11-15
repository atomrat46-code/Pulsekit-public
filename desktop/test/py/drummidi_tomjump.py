# DrumMidi with a kit whose toms are short and dry and with one whose toms ring on (a roomy studio
# kit's): --learn-kit writes "tomjump", the tom band's level jump at the test beat's 2nd and 3rd hit of
# each tom (over the ring of the hit before); the ringing kit's is lower, and with its profile DrumMidi
# asks a smaller level jump of a tom (it says so). Used by DesktopBehavior s261.
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
rng = np.random.default_rng(7)
TOM_HZ = {41: 82, 43: 98, 45: 120, 47: 140, 48: 165, 50: 190}


def sound(n, tom_tau):
    m = int(1.5 * R)
    t = np.arange(m) / R
    if n == 36:
        return 0.9 * np.sin(2 * np.pi * (55 * t + 80 * (1 - np.exp(-t * 30)) / 30)) * np.exp(-t / 0.12)
    if n in TOM_HZ:
        # A stick's click, then the tone ringing for tom_tau seconds.
        click = 0.4 * rng.standard_normal(m) * np.exp(-t / 0.004)
        return 0.8 * np.sin(2 * np.pi * TOM_HZ[n] * t) * np.exp(-t / tom_tau) + click
    if n == 38:
        return (0.5 * rng.standard_normal(m) + 0.4 * np.sin(2 * np.pi * 190 * t)) * np.exp(-t / 0.07)
    if n in (42, 46, 51, 49):
        return 0.3 * np.diff(np.diff(rng.standard_normal(m), prepend=0), prepend=0) * np.exp(-t / (0.02 if n == 42 else 0.4))
    return None


def render(notes, tom_tau):
    out = np.zeros(int((max(t for t, n in notes) + 2) * R))
    for t, n in notes:
        x = sound(n, tom_tau)
        if x is None:
            continue
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
beat = [(t, n) for t, n, _, tag in vr.kit_events() if tag in ('', 'k', 's', 'g', '-')]
# A song: kick and snare, a tom fill each bar.
song = []
for bar in range(8):
    b = 0.5 + bar * 2.0
    song += [(b, 36), (b + 0.5, 38), (b + 1.0, 36), (b + 1.25, 45), (b + 1.5, 47), (b + 1.75, 41)]
for kit, tau in (('dry', 0.08), ('ringing', 0.6)):
    write('beat.wav', render(beat, tau))
    subprocess.run(['java', '-cp', jar, 'DrumMidi_CRT', '--learn-kit', 'beat.wav', 'times.txt', kit + '.kit'], capture_output=True, text=True)
    line = [l for l in open(kit + '.kit') if l.startswith('tomjump')] if os.path.isfile(kit + '.kit') else []
    jump = float(line[0].split()[1]) if line else -1
    write('song.wav', render(song, tau))
    r = subprocess.run(['java', '-cp', jar, 'DrumMidi_CRT', 'song.wav', 'song.mid', '--kit', kit + '.kit'], capture_output=True, text=True)
    said = [l for l in r.stdout.splitlines() if 'jump less over the ring' in l]
    print('%s: tomjump %.2f, %s' % (kit, jump, said[0].strip() if said else 'tom jump as before'))
