# map.txt's "chokes" lines: each plugin's own choke notes (its section's, else the lines before any section),
# the note map left as it is by them, and Check's words for a bad one (as PluginFiles.mapProblems says them).
# Used by DesktopBehavior s255. Arguments: vst_render.py, map.txt.
import importlib.util
import os
import sys
import types

sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')
spec = importlib.util.spec_from_file_location('vr', os.path.abspath(sys.argv[1]))
vr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vr)
path = sys.argv[2]
for plugin in ("C:/VST3/Ugritone Drums.vst3", "C:/VST3/Riot Drums.vst3", "C:/LV2/avldrums.lv2"):
    pairs, part = vr.plugin_chokes(path, plugin)
    print("chokes %s: %s from %r" % (os.path.basename(plugin), vr.chokes_text(pairs) if pairs else "none", part))
print("note map Ugritone:", sorted(vr.map_for(path, "C:/VST3/Ugritone Drums.vst3")[0].items()))
print("avl built in:", vr.chokes_text(vr.AVL_CHOKES), vr.avl_kit("C:/LV2/avldrums.lv2"))
for p in vr.map_problems(open(path).read()):
    print("py: " + p)
