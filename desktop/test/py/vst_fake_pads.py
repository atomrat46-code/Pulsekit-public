# A fake drum plugin with five pads on 48 to 52 (kick, snare, closed and open hat, crash, as DigiDrum's):
# vst_render's Test (--check) through it, without a map and then with the map it suggested in map.txt.
# Used by DesktopBehavior s244. Arguments: vst_render.py, a home folder.
import sys, types, os, io, contextlib, numpy as np
sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')
os.environ['HOME'] = sys.argv[2]; os.environ['USERPROFILE'] = sys.argv[2]
import importlib.util
s = importlib.util.spec_from_file_location('vr', sys.argv[1]); vr = importlib.util.module_from_spec(s); s.loader.exec_module(vr)
R = 44100
rng = np.random.default_rng(1)
def env(n, tau): return np.exp(-np.arange(n) / (tau * R))
def hp(x): return np.diff(np.diff(x, prepend=0), prepend=0)
def sound(kind):
    n = int(1.4 * R); t = np.arange(n) / R
    if kind == 'kick': return 0.9 * np.sin(2 * np.pi * (55 + 80 * np.exp(-t * 30)) * t) * env(n, 0.12)
    if kind == 'snare': return (0.5 * rng.standard_normal(n) + 0.4 * np.sin(2 * np.pi * 190 * t)) * env(n, 0.07)
    if kind == 'chh': return 0.6 * hp(rng.standard_normal(n)) * env(n, 0.02)
    if kind == 'ohh': return 0.5 * hp(rng.standard_normal(n)) * env(n, 0.12)
    if kind == 'crash': return 0.5 * hp(rng.standard_normal(n)) * env(n, 0.5)
PADS = {48: 'kick', 49: 'snare', 50: 'chh', 51: 'ohh', 52: 'crash'}
class Chain:
    note_map = None
    load_limit = 0.0
    fx = None
    def __init__(self, daw, rate, opts, switches): pass
    def latency(self): return 0
    def render(self, np_, midi=None, wav=None, tail=2.0, raw=False):
        if not raw: midi = vr.sent_midi(midi, self.note_map)
        length, notes = vr.midi_notes(midi)[:2]
        out = np.zeros(int((length + tail) * R) + R)
        for t, n in notes:
            if n in PADS:
                x = sound(PADS[n]); a = int(t * R); out[a:a + len(x)] += x[:len(out) - a]
        return np.vstack([out, out]) * 0.5, length, 0
vr.Chain = Chain
vr.WARM_PAUSE = 0
plug = os.path.join(sys.argv[2], 'DigiDrum Pro.vst3'); os.makedirs(plug, exist_ok=True)
def run():
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        try: vr.main(['--plugin', plug, '--check'])
        except SystemExit: pass
    return buf.getvalue()
first = run()
print('first: suggestions %d' % first.count('Map suggestion:'))
for l in first.splitlines():
    if l.startswith(('Notes', 'Pads', 'Working')): print('first ' + l)
lines = [l.split('] ', 1)[1] for l in first.splitlines() if l.startswith('Map suggestion:')]
os.makedirs(os.path.join(sys.argv[2], '.pulsekit', 'maps'), exist_ok=True)
open(os.path.join(sys.argv[2], '.pulsekit', 'maps', 'map.txt'), 'w').write('[DigiDrum Pro]\n' + '\n'.join(lines) + '\n')
second = run()
print('second: suggestions %d' % second.count('Map suggestion:'))
for l in second.splitlines():
    if l.startswith(('Notes', 'Note map')): print('second ' + l)
