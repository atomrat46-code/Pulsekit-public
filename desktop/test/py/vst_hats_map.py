# map.txt's "hats" lines: each plugin's own hi-hat notes (its section's, else the lines before any section;
# AVL Drumkits' built in), the note map left as it is by them, Check's words for a bad one (as
# PluginFiles.mapProblems says them), and the hi-hat check on a fake plugin: its closed, pedal and open hat
# and a semi-open one, each struck alone and with a kick; its pedal silent. Used by DesktopBehavior s256.
# Arguments: vst_render.py, map.txt, DrumMidi_CRT.jar, a work folder.
import importlib.util
import os
import sys
import types

import numpy as np

sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')
spec = importlib.util.spec_from_file_location('vr', os.path.abspath(sys.argv[1]))
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)
path, jar, work = sys.argv[2], os.path.abspath(sys.argv[3]), sys.argv[4]
for plugin in ("C:/VST3/Ugritone Drums.vst3", "C:/VST3/Riot Drums.vst3", "C:/VST3/Other.vst3"):
    hats, part = vr.plugin_hats(path, plugin)
    print("hats %s: %s from %r" % (os.path.basename(plugin), vr.hats_text(hats) if hats else "none", part))
print("note map Ugritone:", sorted(vr.map_for(path, "C:/VST3/Ugritone Drums.vst3")[0].items()))
print("avl built in:", vr.hats_text(vr.AVL_HATS))
for p in vr.map_problems(open(path).read()):
    print("py: " + p)
os.chdir(work)
R = 44100
rng = np.random.default_rng(5)


def noise(m):
    return np.diff(np.diff(rng.standard_normal(m), prepend=0), prepend=0)


def sound(n):
    if n == 36:
        t = np.arange(int(0.4 * R)) / R
        return 0.9 * np.sin(2 * np.pi * 55 * t) * np.exp(-t / 0.12)
    if n == 42:
        m = int(0.12 * R)
        return 0.25 * noise(m) * np.exp(-np.arange(m) / (0.02 * R))
    if n == 45:
        m = int(0.5 * R)
        return 0.25 * noise(m) * np.exp(-np.arange(m) / (0.12 * R))
    if n == 46:
        m = int(1.0 * R)
        return 0.25 * noise(m) * np.exp(-np.arange(m) / (0.35 * R))
    return np.zeros(1)


class Chain:
    note_map = None

    def render(self, np_, midi=None, wav=None, tail=2.0, raw=False):
        assert raw, "the hi-hat check plays the plugin's own notes"
        length, notes = vr.midi_notes(midi)[:2]
        out = np.zeros(int((length + tail) * R) + R)
        for t, n in notes:
            x = sound(n)
            a = int(t * R)
            out[a:a + len(x)] += x[:len(out) - a]
        return np.vstack([out, out]), length, 0


hats = {"closed": 42, "pedal": 44, "semiopen": 45, "open": 46}
for line in vr.hihat_check(Chain(), np, R, 16, {"--java": "java", "--drummidi": jar}, None, hats, 36, ["--faded-opens"]):
    print(line)
