# The note map's check as vst_render says it (map_problems; Pulsekit's Check says the same), the check
# printed at a run's start, and map.txt in the tests' results zip. Used by DesktopBehavior s251.
# Arguments: vst_render.py, a home folder, the map text's file.
import importlib.util
import os
import sys
import types
import zipfile

sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')
home = os.path.abspath(sys.argv[2])
os.environ['HOME'] = home
os.environ['USERPROFILE'] = home
spec = importlib.util.spec_from_file_location('vr', os.path.abspath(sys.argv[1]))
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)
text = open(sys.argv[3], encoding='utf-8').read()
for p in vr.map_problems(text):
    print('py: %s' % p)
os.makedirs(os.path.dirname(vr.note_map_home()), exist_ok=True)
open(vr.note_map_home(), 'w', encoding='utf-8').write(text)
print('run check: %d' % vr.check_note_map(None))
work = os.path.join(home, 'tests')
os.makedirs(work, exist_ok=True)
open(os.path.join(work, 'vst-test-X (default kit).txt'), 'w').write('report\n')
path = vr.results_zip([work])
print('results zip: %s' % sorted(zipfile.ZipFile(path).namelist()))
