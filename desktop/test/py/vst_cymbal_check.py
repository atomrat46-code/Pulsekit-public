# vst_render's cymbal check: a fake plugin's crashes (49, 57, 52), ride (51) and bell (53), each struck alone
# and with a kick; its ride 2 (59) silent, its splash (55) left out by --map. The lines it writes. Used by
# DesktopBehavior s255. Arguments: vst_render.py, DrumMidi_CRT.jar, a work folder.
import importlib.util
import os
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
rng = np.random.default_rng(3)


def sound(n):
    if n == 36:
        t = np.arange(int(0.4 * R)) / R
        return 0.9 * np.sin(2 * np.pi * 55 * t) * np.exp(-t / 0.12)
    if n in (49, 57, 52, 55):
        m = int(1.6 * R)
        return 0.3 * np.diff(np.diff(rng.standard_normal(m), prepend=0), prepend=0) * np.exp(-np.arange(m) / (0.9 * R))
    if n in (51, 53):
        m = int(0.9 * R)
        t = np.arange(m) / R
        return (0.08 * np.diff(np.diff(rng.standard_normal(m), prepend=0), prepend=0) + 0.2 * np.sin(2 * np.pi * (4200 if n == 51 else 2900) * t)) * np.exp(-t / 0.35)
    return np.zeros(1)


class Chain:
    note_map = None

    def render(self, np_, midi=None, wav=None, tail=2.0, raw=False):
        length, notes = vr.midi_notes(midi)[:2]
        out = np.zeros(int((length + tail) * R) + R)
        for t, n in notes:
            x = sound(n)
            a = int(t * R)
            out[a:a + len(x)] += x[:len(out) - a]
        return np.vstack([out, out]), length, 0


for line in vr.cymbal_check(Chain(), np, R, 16, {"--java": "java", "--drummidi": jar}, None, {55: None}, ["--faded-opens"]):
    print(line)
