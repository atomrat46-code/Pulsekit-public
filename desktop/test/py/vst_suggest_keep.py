# The map suggestion with a fat studio snare that reads tom-like (Tight Studio Drums): the snare note is
# not left out (38=none silenced every snare) and no tom is sent to the snare's pad; a real tom-like pad
# elsewhere is still used for the toms. Used by DesktopBehavior s250. Argument: vst_render.py.
import importlib.util
import os
import sys
import types

sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')
spec = importlib.util.spec_from_file_location('vr', os.path.abspath(sys.argv[1]))
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)
kinds = {36: 'kick', 38: 'tom', 42: 'hat', 46: 'hat', 49: 'cymbal', 51: 'cymbal', 47: 'tom', 48: 'tom'}
pads = dict((n, {'pitch': 100.0 + n, 'centroid': 1000.0 + n, 'ring': 0.1 + n / 100.0, 'flat': 0.2, 'level': 1.0}) for n in kinds)
vr.pad_kinds = lambda p: kinds
out = vr.suggest_map(pads)
print('suggestion: %s' % [(a, b) for a, b, _ in out])
print('38 kept: %s | no tom to 38: %s' % (all(a != 38 for a, b, _ in out), all(b != 38 for a, b, _ in out)))
