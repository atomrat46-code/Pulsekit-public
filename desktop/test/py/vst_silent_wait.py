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
# --max_wait: longer than the Stop timer's longest (40 s): waited that long; 0 or shorter: the 40 s.
for mw in (60, 10, 0):
    vr.MAX_WAIT[0] = float(mw)
    print('max_wait %d: waits %.0f' % (mw, vr.silent_wait(Chain(0))))
vr.MAX_WAIT[0] = 0.0
# The preflight (a test's start): a crash silent all along stops the run when this kit played it before
# (Test or an earlier run), and not when it never did (a kit without one).
JAR = sys.argv[3] if len(sys.argv) > 3 else ''
class Plugin(Chain):
    def __init__(self, daw, rate, opts, switches):
        Chain.__init__(self, 10 ** 6)
        self.fx = None
        self.load_limit = 0.0
        self.load_measured = False
        self.longest_load = 0.0
    def latency(self): return 0
vr.Chain = Plugin
vr.WARM_PAUSE = 0
os.makedirs(plug, exist_ok=True)
def pre(kit):
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        song = vr.notes_midi(os.path.join(sys.argv[2], 'song.mid'), [(0.2, 36, 100), (0.7, 38, 100)])
        try: vr.main([song, '--plugin', plug, '--test', '--preflight', '--kit_name', kit, '--drummidi', JAR, '--no-cymbal-check'])
        except SystemExit: pass
    return [l for l in buf.getvalue().splitlines() if l.startswith(('Preflight', 'Failed'))]
vr.CURRENT_KIT[0] = 'No Crash'
print('new kit: %s | heard kept: 49 in it %s' % (' | '.join(pre('No Crash')), 49 in vr.heard_before(plug)))
vr.CURRENT_KIT[0] = 'Rock'
vr.note_heard(plug, [36, 38, 49])
print('crash played before: %s' % ' | '.join(pre('Rock')))
# Two plugins: the second's kit (Rock) played the crash before: the preflight stops the run, nothing rendered.
plug2 = os.path.join(sys.argv[2], 'Other Drums.vst3'); os.makedirs(plug2, exist_ok=True)
buf = io.StringIO()
with contextlib.redirect_stdout(buf):
    song = vr.notes_midi(os.path.join(sys.argv[2], 'song.mid'), [(0.2, 36, 100), (0.7, 38, 100)])
    try: vr.main([song, '--plugin', plug2, '--kit_name', 'Fine', '--plugin2', plug, '--kit_name2', 'Rock', '--test', '--drummidi', JAR, '--no-cymbal-check'])
    except SystemExit: pass
out = buf.getvalue()
print('two plugins: %s | rendered %s' % (' | '.join(l for l in out.splitlines() if l.startswith(('== Preflight', 'Preflight', 'Failed: preflight'))),
                                         any(f.endswith('.wav') and 'song' in f for f in os.listdir(sys.argv[2]))))
