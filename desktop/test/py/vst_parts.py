# vst_render --parts: the drums split between plugin rows and played in one run. The same SoundFont as
# plugin 1 and (copied) plugin 2, kick and cymbals by plugin 2: the mixed WAV sounds as the whole kit
# played by one; the notes each row plays; the files named after both; bad --parts refused.
# Used by DesktopBehavior s249. Arguments: vst_render.py, DrumMidi_CRT.jar, a SoundFont, a work folder.
import importlib.util
import os
import shutil
import sys
import wave

import numpy as np

sys.modules.pop('dawdreamer', None)
spec = importlib.util.spec_from_file_location('vr', os.path.abspath(sys.argv[1]))
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)
jar, sf, work = os.path.abspath(sys.argv[2]), os.path.abspath(sys.argv[3]), os.path.abspath(sys.argv[4])
os.chdir(work)
shutil.copy(sf, 'kit-a.sf2')
shutil.copy(sf, 'kit-b.sf2')
vr.notes_midi('x.mid', [(0.25 * i, n, 110) for i, n in enumerate([36, 42, 38, 42, 36, 49, 38, 46, 45, 41] * 2)])


def run(args):
    try:
        vr.main(args)
        return 'done'
    except SystemExit as e:
        return str(e)


def read(path):
    w = wave.open(path)
    a = np.frombuffer(w.readframes(w.getnframes()), dtype='<i2').astype(float) / 32768
    return a


p = vr.parse_parts('kick=2,snare=1,toms=1,cymbals=3')
print('notes: 2 %s | 3 %s | 1 %s' % (sorted(vr.parts_notes(p, 2)), sorted(vr.parts_notes(p, 3)), sorted(vr.parts_notes(p, 1))))
p = vr.parse_parts('hats=2,cymbals=3')
print('hats apart: 2 %s | 3 %s' % (sorted(vr.parts_notes(p, 2)), sorted(vr.parts_notes(p, 3))))
print('text: %s' % vr.parts_text(vr.parse_parts('kick=2,cymbals=2'), {1: 'A.vst3', 2: 'B.sf2'}))
print('whole: %s' % run(['x.mid', 'whole.wav', '--plugin', 'kit-a.sf2', '--drummidi', jar]))
print('parts: %s' % run(['x.mid', '--plugin', 'kit-a.sf2', '--plugin2', 'kit-b.sf2', '--parts', 'kick=2,cymbals=2', '--drummidi', jar]))
made = [f for f in os.listdir('.') if f.endswith('(parts).wav')]
print('made: %s' % made)
if made:
    a, b = read('whole.wav'), read(made[0])
    n = min(len(a), len(b))
    print('same sound: %s (peak %.2f)' % (bool(np.abs(a[:n] - b[:n]).max() < 0.02 and np.abs(a).max() > 0.1), np.abs(b).max()))
print('bad: %s' % run(['x.mid', '--plugin', 'kit-a.sf2', '--parts', 'bells=2']))
print('missing: %s' % run(['x.mid', '--plugin', 'kit-a.sf2', '--parts', 'kick=3']))
