# vst_render --chokes: the choke beat through a fake plugin with a crash on 49 and the same crash choked
# (its ring cut at 120 ms) on 50: each played alone, DrumMidi (--chokes 49=50) asked what it heard, and the
# lines saying what each was written as. Also DrumMidi's --chokes taken (its Chokes line) and refused when
# it is not note=note. Used by DesktopBehavior s246. Arguments: vst_render.py, DrumMidi_CRT.jar, a work folder.
import importlib.util
import os
import subprocess
import sys
import types

import numpy as np

sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')
spec = importlib.util.spec_from_file_location('vr', os.path.abspath(sys.argv[1]))
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)
jar, work = os.path.abspath(sys.argv[2]), sys.argv[3]
os.chdir(work)
R = 44100
rng = np.random.default_rng(7)


def crash(choked):
    n = int(1.4 * R)
    x = np.diff(np.diff(rng.standard_normal(n), prepend=0), prepend=0) * 0.25 * np.exp(-np.arange(n) / (0.9 * R))
    if choked:
        cut = int(0.12 * R)
        x[cut:] *= np.exp(-np.arange(n - cut) / (0.01 * R))
    return x


class Chain:
    note_map = None

    def render(self, np_, midi=None, wav=None, tail=2.0, raw=False):
        length, notes = vr.midi_notes(midi)[:2]
        out = np.zeros(int((length + tail) * R) + R)
        for t, n in notes:
            if n in (49, 50):
                x = crash(n == 50)
                a = int(t * R)
                out[a:a + len(x)] += x[:len(out) - a]
        return np.vstack([out, out]), length, 0


opts = {"--java": "java", "--drummidi": jar}
lines = vr.choke_beat(Chain(), np, R, 16, opts, None, {49: 50})
for l in lines:
    print(l)
r = subprocess.run(['java', '-cp', jar, 'DrumMidi_CRT', 'x.wav', 'x.mid', '--chokes', '49'], capture_output=True, text=True)
print('refused: %s' % [l for l in r.stdout.splitlines() if 'chokes' in l.lower()][-1:])
