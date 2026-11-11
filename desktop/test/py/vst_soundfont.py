# vst_render with a SoundFont as its plugin: Midi2Wav (in DrumMidi_CRT.jar) plays the MIDI with it, no
# DawDreamer or Carla; a SoundFont in an LV2 bundle (x42 AVL Drumkits' kits) plays the same, said so;
# --edit refused; AVL's kits get its built-in note map (48=47, 50=45: its 48 is a hat, 50 a choke). Used by
# DesktopBehavior s248.
# Arguments: vst_render.py, DrumMidi_CRT.jar, a SoundFont, a work folder.
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
vr.notes_midi(os.path.join(work, 'x.mid'), [(0.25 * i, n, 110) for i, n in enumerate([36, 42, 38, 42, 36, 42, 38, 49])])
bundle = os.path.join(work, 'avldrums.lv2')
os.makedirs(bundle, exist_ok=True)
shutil.copy(sf, os.path.join(bundle, 'Black_Pearl_4_LV2.sf2'))


def run(args):
    try:
        vr.main(args)
        return 'done'
    except SystemExit as e:
        return str(e)


for label, plugin, out in (('sf2', sf, 'x-sf.wav'), ('lv2 kit', os.path.join(bundle, 'Black_Pearl_4_LV2.sf2'), 'x-avl.wav')):
    said = run(['x.mid', out, '--plugin', plugin, '--drummidi', jar, '--rate', '44100'])
    if os.path.isfile(out):
        w = wave.open(out)
        a = np.frombuffer(w.readframes(w.getnframes()), dtype='<i2')
        print('%s: %s | %d Hz, %.1f s, peak %.2f' % (label, said, w.getframerate(), w.getnframes() / w.getframerate(), np.abs(a).max() / 32768))
    else:
        print('%s: %s | no WAV' % (label, said))
print('avl: %s' % [vr.avl_kit(n) for n in ('avldrums', 'Black_Pearl_4_LV2', 'Red_Zeppelin_4_LV2.sf2', 'Blonde_Bop_HR_LV2', 'Black Pearl Drums', 'test-kit')])
print('avl map: %s' % (vr.pad_map(None, 'Black_Pearl_4_LV2')[1],))
print('edit: %s' % run(['x.mid', '--plugin', sf, '--drummidi', jar, '--edit']))
print('no jar: %s' % run(['x.mid', '--plugin', sf]))
