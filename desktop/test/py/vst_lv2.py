# vst_render and an LV2 plugin (x42 AVL Drumkits' bundle as Windows installs it: avldrums.lv2 with
# avldrums.dll, its .ttl files and a SoundFont a kit): the bundle or its .dll plays in Carla's rack, its kit
# (a plugin of its own) picked by --clap_id; its Carla project block; Edit settings' project per kit; a
# SoundFont is not taken for the bundle (it plays with Midi2Wav: vst_soundfont.py). Used by DesktopBehavior s247.
# v421: AVL's own files, plugins named with a prefix (avldrums:BlackPearl); the bundle's folder in LV2_PATH.
# Arguments: vst_render.py, a home folder.
import importlib.util
import os
import sys
import types

sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')
home = os.path.abspath(sys.argv[2])
os.environ['HOME'] = home
os.environ['USERPROFILE'] = home
spec = importlib.util.spec_from_file_location('vr', os.path.abspath(sys.argv[1]))
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)
os.chdir(home)
bundle = os.path.join(home, 'LV2', 'avldrums.lv2')
os.makedirs(bundle, exist_ok=True)
open(os.path.join(bundle, 'manifest.ttl'), 'w').write('''@prefix lv2:  <http://lv2plug.in/ns/lv2core#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

<http://gareus.org/oss/lv2/avldrums#BlackPearl>
  a lv2:Plugin ;
  lv2:binary <avldrums.dll> ;
  rdfs:seeAlso <avldrums.ttl> .

<http://gareus.org/oss/lv2/avldrums#RedZeppelin>
  a lv2:Plugin ;
  lv2:binary <avldrums.dll> ;
  rdfs:seeAlso <avldrums.ttl> .
''')
open(os.path.join(bundle, 'avldrums.ttl'), 'w').write('''@prefix doap: <http://usefulinc.com/ns/doap#> .
@prefix lv2:  <http://lv2plug.in/ns/lv2core#> .

<http://gareus.org/oss/lv2/avldrums#BlackPearl>
  a lv2:Plugin, lv2:InstrumentPlugin ;
  doap:license <http://usefulinc.com/doap/licenses/gpl> ;
  doap:name "AVL Drumkits Black Pearl" ;
  lv2:optionalFeature lv2:hardRTCapable .

<http://gareus.org/oss/lv2/avldrums#RedZeppelin>
  a lv2:Plugin, lv2:InstrumentPlugin ;
  doap:name "AVL Drumkits Red Zeppelin" ;
  lv2:optionalFeature lv2:hardRTCapable .
''')
for n in ('avldrums.dll', 'Black_Pearl_4_LV2.sf2', 'Red_Zeppelin_4_LV2.sf2'):
    open(os.path.join(bundle, n), 'wb').write(b'MZ')
rack = os.path.join(home, 'Carla', 'Carla.vst', 'CarlaVstShell.dll')
os.makedirs(os.path.dirname(rack), exist_ok=True)
open(rack, 'wb').write(b'MZ')
os.makedirs(os.path.join(home, '.pulsekit'), exist_ok=True)
open(os.path.join(home, '.pulsekit', 'carla-rack.txt'), 'w').write(rack)
open(os.path.join(home, 'x.mid'), 'wb').write(vr.notes_midi(os.path.join(home, 'x.mid'), [(0.5, 36, 100)]) and open(os.path.join(home, 'x.mid'), 'rb').read())
print('plugins: %s' % vr.lv2_plugins(bundle))
# AVL Drumkits' own files name each plugin with a prefix (avldrums:BlackPearl), as installed.
real = os.path.join(home, 'LV2real', 'avldrums.lv2')
os.makedirs(real, exist_ok=True)
open(os.path.join(real, 'manifest.ttl'), 'w').write('''@prefix lv2:  <http://lv2plug.in/ns/lv2core#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix ui:   <http://lv2plug.in/ns/extensions/ui#> .

@prefix avldrums: <http://gareus.org/oss/lv2/avldrums#> .

avldrums:BlackPearl
\ta lv2:Plugin ;
\tlv2:binary <avldrums.dll>  ;
\trdfs:seeAlso <avldrums.ttl> .

avldrums:BlackPearlMulti
\ta lv2:Plugin ;
\tlv2:binary <avldrums.dll>  ;
\trdfs:seeAlso <avldrums.ttl> .

avldrums:ui_gl
\ta ui:WindowsUI ;
\tui:binary <avldrumsUI_gl.dll> .
''')
open(os.path.join(real, 'avldrums.ttl'), 'w').write('''@prefix doap:  <http://usefulinc.com/ns/doap#> .
@prefix lv2:   <http://lv2plug.in/ns/lv2core#> .
@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
@prefix ui:    <http://lv2plug.in/ns/extensions/ui#> .
@prefix avldrums: <http://gareus.org/oss/lv2/avldrums#> .

avldrums:BlackPearl
  a doap:Project, lv2:InstrumentPlugin, lv2:Plugin ;

  doap:name "Black Pearl Drumkit" ;
  rdfs:comment "AVLinux MIDI Drumkit" ;
  ui:ui avldrums:ui_gl ;
  lv2:port [
      lv2:index 0 ;
      lv2:name "Midi In" ;
  ] .

avldrums:BlackPearlMulti
  a doap:Project, lv2:InstrumentPlugin, lv2:Plugin ;

  doap:name "Black Pearl Drumkit Multi" ;
  ui:ui avldrums:ui_gl .
''')
print('real plugins: %s' % vr.lv2_plugins(real))
seen = {}


class C:
    def __init__(self, daw, rate, opts, switches):
        seen.update(opts)
        raise SystemExit('chain')


vr.Chain = C


def run(args):
    seen.clear()
    vr.LV2_WANTED['id'] = None
    try:
        vr.main(args)
    except SystemExit as e:
        return str(e)


for label, args in (('dll', ['x.mid', '--plugin', os.path.join(bundle, 'avldrums.dll')]),
                    ('bundle, Red', ['x.mid', '--plugin', bundle, '--clap_id', 'Red Zeppelin'])):
    said = run(args)
    print('%s: %s | plugin %s, bridged %s | %s | project %s' % (label, said, os.path.basename(seen.get('--plugin') or ''), os.path.basename(seen.get('--bridged') or ''),
                                                             vr.rack_said(seen.get('--bridged')) if seen.get('--bridged') else '-',
                                                             os.path.basename(vr.bridged_project(seen['--bridged'])) if seen.get('--bridged') else '-'))
vr.LV2_WANTED['id'] = 'Red'
print('LV2_PATH: %s' % os.path.basename(os.environ.get('LV2_PATH', '').split(os.pathsep)[0]))
print('block: %s' % vr.bridged_carla_plugin(bundle).replace('\n', ' '))
