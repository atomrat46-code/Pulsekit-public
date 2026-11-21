# Silent notes waited for: a fake plugin whose crash (49) is silent for its first renders, as a kit still
# loading. The notes check plays the silent notes again (every 5 s, here without the sleep) for up to the
# plugin's longest Stop timer time, which Edit settings keeps as the longest (a shorter one later keeps it).
# Used by DesktopBehavior s270. Arguments: vst_render.py, a home folder.
import sys, types, os, io, contextlib, numpy as np
sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')
os.environ['HOME'] = sys.argv[2]; os.environ['USERPROFILE'] = sys.argv[2]
os.chdir(sys.argv[2])
import importlib.util
s = importlib.util.spec_from_file_location('vr', sys.argv[1]); vr = importlib.util.module_from_spec(s); s.loader.exec_module(vr)
import time
time.sleep = lambda x: None
R = 44100
plug = os.path.join(sys.argv[2], 'SSDSampler5.dll')
# Edit settings' Stop timer: 40 s, then 12 s for another kit; the longest stays 40.
vr.note_longest_load(plug, 40.0)
vr.note_longest_load(plug, 12.0)
print('longest: %.0f' % vr.longest_load(plug))
class Chain:
    note_map = None
    load_limit = 12.0
    load_measured = True
    def __init__(self, loads):
        self.loads = loads
        self.calls = 0
        self.longest_load = vr.longest_load(plug)
    def render(self, np_, midi=None, wav=None, tail=2.0, raw=False):
        length, notes = vr.midi_notes(midi)[:2]
        out = np.zeros(int((length + tail) * R) + R)
        for t, n in notes:
            if n == 49:
                self.calls += 1
                if self.calls <= self.loads: continue
            a = int(t * R); x = 0.5 * np.sin(2 * np.pi * 200 * np.arange(R) / R) * np.exp(-np.arange(R) / (0.2 * R))
            out[a:a + R] += x[:len(out) - a]
        return np.vstack([out, out]), length, 0
for loads, name in ((2, 'loading'), (10 ** 6, 'never')):
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        lines, silent = vr.drum_notes_check(Chain(loads), np, R, {})
    print('%s: silent %s | %s' % (name, sorted(silent), ' | '.join(l for l in lines if 'silent' in l)))
