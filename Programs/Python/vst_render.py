"""
vst_render.py: plays a MIDI file through a VST2 or VST3 instrument and saves what it sounds like as a WAV.

Usage: python vst_render.py [song.mid] [output.wav] [--plugin <plugin>] [--plugin2 <plugin>] [--plugin3 <plugin>] [--plugin4 <plugin>] [--carla <carlaproject>] [--clap <clapplugin>] [--clap_id ID] [--drumgizmo <drumkit>] [--midimap <mapfile>] [--state <file>] [--wav <input.wav>] [--effect <plugin>] [--effect_state <file>] [--bpm N] [--bpm_from_name] [--rate N] [--tail N] [--volume N] [--bits N] [--edit] [--dir <folder>] [--dir2 <folder>] [--recursive] [--outdir <folder>] [--skip_existing] [--test] [--no-toms] [--snare-leak] [--faded-opens] [--no-kit] [--compare_kits] [--map <notes>] [--no-map] [--map2 <notes>] [--map3 <notes>] [--map4 <notes>] [--note_map <notes>] [--chokes <notes>] [--kit_name NAME] [--kit_name2 NAME] [--kit_name3 NAME] [--kit_name4 NAME] [--mp3 KBPS] [--keep_wav] [--zip] [--zip_mb N] [--zip_outdir <folder>] [--zip_nowav] [--check] [--scan]

  song.mid        the MIDI played (every channel; a drum plugin hears the drum channel as it is).
  output.wav      the WAV made; <song>-<plugin>.wav when it is not given.
  --plugin        the instrument: a VST2 (.dll, .so, .vst) or VST3 (.vst3) plugin. With --wav, an
                  effect (an amp, a reverb) that the WAV is played through instead. A SoundFont
                  (.sf2, .sfz: x42 AVL Drumkits' kits too) plays with Midi2Wav (in --drummidi's jar),
                  its drum kit (bank 128, else its first preset): no DawDreamer, Carla or --edit.
  --plugin2, --plugin3, --plugin4
                  more instruments: the whole run (renders, test, MP3s, zip) again with each, after
                  --plugin's: <midi>-<plugin2>.wav and its own report. Each plays with the settings
                  last saved for it with --edit (--state is --plugin's). A plugin that fails is
                  reported and the next one goes on.
  --carla         a Carla project (.carxp, saved in Carla): its plugins play the MIDI, in the Carla-Rack
                  VST (found in the VST folders unless --plugin gives it). Carla hosts LV2, VST2, VST3,
                  SFZ and SoundFonts natively on Linux, and Windows plugins bridged with yabridge, so
                  a Linux computer renders without Windows. Set the kit up in Carla (the plugin's own
                  window), save the project, and give it here; no --edit needed.
  --clap          a CLAP plugin (.clap): Carla's Carla-Rack VST hosts it (Carla 2.6 or newer, free at
                  kx.studio/carla), so no Carla project is needed. --plugin x.clap does the same.
                  --edit opens the rack with the plugin in it: double-click the plugin for its own
                  window, load a kit or patch, close the windows; it plays with them from then on.
  --clap_id       which plugin of a .clap file that has several (its id; --scan lists them).
                  Without it, the file's first plugin.
  --drumgizmo     a DrumGizmo kit (its drumkit .xml, free kits at drumgizmo.org): DrumGizmo's own
                  program plays the MIDI (Linux; no DawDreamer or plugin needed), its channels
                  (kick, snare, overheads...) mixed to stereo, the ...L and ...R ones to their side.
  --midimap       the kit's MIDI map (.xml): which note plays which drum. Without it, the kit
                  folder's Midimap*.xml is used.
  --state         the plugin's settings (its kit, patch, mixer): a file --edit saved, a .fxp/.fxb
                  (VST2) or a .vstpreset (VST3). Without it, the settings last saved with --edit for
                  this plugin are used (~/.pulsekit/vst/<plugin>.state), else the plugin's defaults.
  --wav           a WAV to play through --plugin (an effect) instead of MIDI.
  --effect        an effect after the instrument (an amp sim, a reverb); --effect_state its settings,
                  as --state (its last --edit settings when not given).
  --bpm N         plays the MIDI at N beats a minute (20 to 400): for a MIDI file with no tempo in it,
                  which plays at 120 otherwise (a DAW may have shown it at its project's tempo), or to
                  hear one faster or slower. The file's own tempo changes are replaced by this one; the
                  file itself is not changed. With --test, its hits are compared at this tempo too.
                  (Pulsekit does not pass its own tempo as --bpm: give it in Params.)
  --bpm_from_name a MIDI file with no tempo of its own (it would play at 120) plays at the tempo its
                  name gives: a number from 40 to 300 in the file's name (127bpmTinytownBeat9,
                  "100 Basic 1 CH", "090 S01 Chorus"), else in its folders' names, nearest first
                  ("200 punk/S10 Outro"). A number with bpm after it counts first; one stuck to
                  letters or in 7-4 does not (S10, F4, 7-4). A file's own tempo is kept (its line
                  says when its name gives another: a half-time groove is named after the song's
                  tempo); one with neither plays at --bpm when given. Each WAV's line says the
                  tempo it was played at.
  --rate N        sample rate: 44100 (default) or 48000.
  --tail N        seconds rendered after the last note, for the sound to ring out (2).
  --volume N      percent, 1 to 400 (100). A mix that would clip is turned down to fit.
  --bits N        16 (default) or 24-bit WAV.
  --edit          opens the plugin's own window first (Windows and Mac): load a kit or patch there,
                  close the window, and its settings are saved (to --state, else
                  ~/.pulsekit/vst/<plugin>.state) and used for this and later runs. With --effect,
                  the effect's window opens after the instrument's.
  --check         plays a short test beat (kick, snare and hats: GM notes 36, 38, 42; then the kick
                  and each tom alone; each cymbal alone, soft to hard, and with a kick and with a
                  snare; the closed hat and the ride played on in eighths) through the plugin and writes it as vst-check-<plugin>.wav (in --outdir, else here): does the
                  plugin play with these settings? Every run does that check first, and a plugin that
                  is silent for it is skipped (with --plugin2 to --plugin4 the next one goes on): load
                  a kit with --edit. In a run, a silent render is left out (no test, no MP3 of it),
                  and the first three renders silent stop the plugin's run.
  --dir           renders every MIDI file in the folder or .pls playlist (the plugin loads once): <midi>-<plugin>.wav
                  beside each, or in --outdir (its subfolders kept). --recursive: subfolders too.
                  SogniMusic's zips (--zip, made on the phone) in the folder (--recursive: in its
                  subfolders too), or one given as --dir, are unpacked first: their MIDI files (the parts of a split zip together) go into
                  <zip>-midi/ beside them and are rendered with the rest.
  --dir2          a second folder (fills here, longer patterns there): its MIDI files rendered in the
                  same run. In --outdir each folder's WAVs go under its own name (fills/..., patterns/...).
  --skip_existing with --dir: a MIDI whose WAV is already there (from a run that was stopped) is not
                  rendered again; that WAV is tested and zipped as the new ones are. With --test, a
                  WAV's DrumMidi MIDI (<wav>-dm.mid) newer than the WAV is used as it is, not made again
                  (leave this off after DrumMidi_CRT or --no-toms changed).
  --test          then runs DrumMidi_CRT on each WAV and compares its hits with the MIDI played, per
                  drum family (within 30 ms, after the best shift): a report, vst-test-<plugin>.txt,
                  and DrumMidi's MIDI as <wav>-dm.mid. Pulsekit gives its DrumMidi_CRT.jar (--drummidi)
                  and Java (--java). --no-toms: DrumMidi writes no toms and the report leaves them out.
                  The test beat as played is kept too: vst-check-<plugin>.wav (an MP3 with --mp3) and
                  its notes' times, vst-check-<plugin>-times.txt (what the kit profile is learned from),
                  and its hits cut one by one into vst-hits-<plugin>/ (with hits.txt), which Pulsekit
                  adds to its drum sound library.
                  Each test run then puts every report, test beat, times file and kit profile in the
                  output folder into one zip, vst-test-results.zip (added to by each run: after runs of
                  several plugins it holds them all) and deletes the loose reports, test beats and times
                  files; the kit profiles stay (--compare_kits reads them). The hits folders go in the
                  zip too (as vst-hits-<plugin>/...) and stay on disk (the drum library links to them).
  --snare-leak    with --test: DrumMidi leaves out the hats a bright snare's wires make in the hat band
                  (its --snare-leak): fewer false hats on the snare, some real ones lost.
  --faded-opens   with --test: DrumMidi writes the open hats that fade like closed ones as closed
                  (its --faded-opens): many more closed hats right, some real open hats lost.
  --no-kit        with --test: no kit profile. Otherwise the test beat's kick and toms (each played
                  alone, as --map says the plugin plays them) are learned by DrumMidi (--learn-kit)
                  into vst-kit-<plugin>.kit (in --outdir, else here), and DrumMidi tells the kicks
                  and toms it finds apart by them (--kit).
  --compare_kits  with --test: DrumMidi on each render again with other kit profiles, and the report
                  compares them: no profile, Pulsekit's reference profile (kick and toms: the average
                  of five free DrumGizmo kits'), and each kit profile earlier tests left beside this
                  one's (vst-kit-*.kit). Whether another kit's profile, or the reference, serves this
                  kit as well as its own. DrumMidi runs once more a render for each.
  --map notes     with --test: what the plugin really plays for the MIDI's notes, so the report
                  compares DrumMidi with that: 45=46,41=none,57=49 (a note to another, or none: the
                  plugin plays nothing there, or nothing the test should count). Or a text file of
                  such pairs (one or more on a line, # comments). The MIDI files themselves are not
                  changed; the report says which map it used. A file can have a section per plugin:
                  [Anarchy Drums] on a line, then its pairs; the section named in the plugin's file
                  name is used (lines before any section are for every plugin), so one file serves
                  --plugin and --plugin2 to --plugin4. maps/drum-kits.txt (next to this script) has
                  the kits tested so far (Anarchy Drums).
  --map2 to --map4  --plugin2's to --plugin4's own --map (pairs or a file); without one, --map is theirs too.
                  A map file's bare name (drum-kits.txt) is looked for in ~/.pulsekit/maps and in the
                  maps folder beside this script.
  --note_map notes  the notes sent to the plugin: the MIDI's note to the pad the plugin plays it
                  with (36=48: the kick plays the plugin's pad 48; 41=none: not played). For a plugin
                  whose pads are on other notes than the GM drums' (DigiDrum's P1 to P5 Note). Pairs,
                  or a text file of them with [plugin name] sections, as --map. Empty: Pulsekit's
                  ~/.pulsekit/maps/map.txt (Files & locations: Edit), when it has lines for the plugin.
                  The MIDI files themselves are not changed.
  --chokes notes  with --test or --check: the plugin's choke notes, a cymbal's note = its choked one
                  (x42 AVL Drumkits: 49=50,51=52,57=58). After the test beat a choke beat plays each
                  cymbal and its choke alone, and DrumMidi (--chokes, with the kit profile) is asked what
                  it hears there: the report says what each was written as.
  --no-map        with --test: no map. Without --map, a plugin drum-kits.txt has a section for
                  (Anarchy, Riot, Ugritone/KVLT, Northern Artillery) is tested with that section.
  --kit_name NAME the kit loaded in the plugin (KVLT's "Finnish Metal 2000"): plugins do not say it,
                  so it is given here. It goes in the files' names after the plugin's
                  (<midi>-Ugritone Drums (Finnish Metal 2000).wav, the report, the kit profile and
                  the zip) and in the report. --kit_name2 to --kit_name4: --plugin2's to --plugin4's.
                  Left blank: "default kit" (the plugin's own; Anarchy and Riot have one), or
                  "saved kit" when a kit saved with --edit (or --state) is used.
  --mp3 KBPS      then turns each WAV into an MP3 at KBPS (128, 192, 256, 320...) with Pulsekit's
                  Wav2Mp3 (in its DrumMidi_CRT.jar: --drummidi and --java, which Pulsekit gives) and
                  deletes the WAV: a 20 s WAV of 3.5 MB is about 0.5 MB at 192. With --test the WAVs
                  are tested first. The zip then has the MP3s (mp3/). --keep_wav: the WAVs stay too.
                  With --skip_existing, an MP3 already made counts as the MIDI's render.
  --zip           then puts the MIDI files played (and the copies at --bpm's tempo), the WAVs made, and
                  with --test DrumMidi's MIDI files and the report, into one zip:
                  vst-render-<plugin>-<date>-<time>.zip beside the report (in --outdir when given). The
                  plugin and Carla settings files given go in too, and the switches used (vst-render-run.txt).
                  With --test the test beat, its times and the kit profile go in check/.
                  Without --zip, a --dir or --test run in a terminal asks at the end whether to make it.
  --zip_mb N      zips of at most N MB each (--zip implied): vst-render-...-part1of3.zip and so on, for a
                  chat or mail that takes files up to a size (30 for 30 MB). Each MIDI stays with its WAV
                  and DrumMidi's MIDI, and each zip has the report, the settings and vst-render-run.txt.
  --zip_outdir F  then copies the run's zips (and with --test vst-test-results.zip) into folder F too,
                  made when missing: one place to pick them up from (a cloud or chat folder). F can
                  have % codes as Pulsekit's Downloads subfolders: after a %, dd day, mm month, yyyy
                  or yy year (d, m: no leading 0) and n the first number whose folder is not there yet
                  (D:\\pickup\\%ddmmyyyy, D:\\pickup\\run-%n). Filled in once: with --plugin2 to
                  --plugin4 every plugin's zips go in the same folder.
  --zip_nowav     the zip without the WAVs (--zip implied): the MIDI files, DrumMidi's MIDI files and the
                  report are small, the WAVs are most of it (a 20 s WAV is about 3.5 MB). A thousand
                  tests fit in one zip this way; send the WAVs of the files that matter by themselves.
                  The MP3s of --mp3 still go in (they are small).
  --scan          lists the plugins in the usual VST folders, then stops.

The plugins' own delay (lookahead, oversampling) is taken off the start, so the hits land where
the MIDI has them.

Carla (free) can be the plugin too: its Carla-Rack or Carla-Patchbay VST hosts other plugins
(also LV2, SFZ and SoundFonts); set it up with --edit, or on Linux with --carla and a project saved
in Carla.

--drumgizmo needs DrumGizmo (Linux: apt install drumgizmo) and numpy; the rest needs DawDreamer and
numpy (pip install dawdreamer), on Windows, Mac or Linux (64-bit Python 3.9+).
A plugin is a desktop program: this does not run on a phone. Licensed plugins render as they do in a
DAW once they are installed and activated on the computer.
"""
import json
import os
import zipfile
import re
import struct
import sys
import wave

PLUGIN_ENDS = (".vst3", ".dll", ".so", ".vst", ".component")


def fail(message, code=1):
    print("Failed: " + message)
    sys.exit(code)


def vst_folders():
    """The usual VST2 and VST3 folders of this computer that exist."""
    home = os.path.expanduser("~")
    if sys.platform.startswith("win"):
        pf = os.environ.get("ProgramFiles", r"C:\Program Files")
        common = os.environ.get("CommonProgramFiles", os.path.join(pf, "Common Files"))
        dirs = [os.path.join(common, "VST3"), os.path.join(common, "VST2"), os.path.join(common, "Steinberg", "VST2"),
                os.path.join(pf, "VSTPlugins"), os.path.join(pf, "Steinberg", "VSTPlugins"), os.path.join(pf, "VST2"),
                os.path.join(pf, "VST")]
    elif sys.platform == "darwin":
        dirs = ["/Library/Audio/Plug-Ins/VST3", "/Library/Audio/Plug-Ins/VST",
                os.path.join(home, "Library/Audio/Plug-Ins/VST3"), os.path.join(home, "Library/Audio/Plug-Ins/VST")]
    else:
        dirs = [os.path.join(home, ".vst3"), os.path.join(home, ".vst"), "/usr/lib/vst3", "/usr/lib/vst",
                "/usr/local/lib/vst3", "/usr/local/lib/vst"]
    return [d for d in dirs if os.path.isdir(d)]


def lv2_folders():
    """The usual LV2 folders of this computer that exist (their plugins play through --carla)."""
    home = os.path.expanduser("~")
    if sys.platform.startswith("win"):
        dirs = [os.path.join(os.environ.get("CommonProgramFiles", r"C:\Program Files\Common Files"), "LV2"), os.path.join(os.environ.get("APPDATA", home), "LV2")]
    elif sys.platform == "darwin":
        dirs = ["/Library/Audio/Plug-Ins/LV2", os.path.join(home, "Library/Audio/Plug-Ins/LV2")]
    else:
        dirs = [os.path.join(home, ".lv2"), "/usr/lib/lv2", "/usr/local/lib/lv2", "/usr/lib/x86_64-linux-gnu/lv2"]
    return [d for d in dirs if os.path.isdir(d)]


def clap_folders():
    """The usual CLAP folders of this computer that exist (their plugins play through --clap)."""
    home = os.path.expanduser("~")
    if sys.platform.startswith("win"):
        dirs = [os.path.join(os.environ.get("CommonProgramFiles", r"C:\Program Files\Common Files"), "CLAP"),
                os.path.join(os.environ.get("LOCALAPPDATA", home), "Programs", "Common", "CLAP")]
    elif sys.platform == "darwin":
        dirs = ["/Library/Audio/Plug-Ins/CLAP", os.path.join(home, "Library/Audio/Plug-Ins/CLAP")]
    else:
        dirs = [os.path.join(home, ".clap"), "/usr/lib/clap", "/usr/local/lib/clap", "/usr/lib/x86_64-linux-gnu/clap"]
    extra = os.environ.get("CLAP_PATH")
    if extra:
        dirs = extra.split(os.pathsep) + dirs
    return [d for d in dirs if os.path.isdir(d)]


def clap_binary(path):
    """The library inside a .clap: the file itself, or on a Mac the bundle's Contents/MacOS program."""
    if os.path.isdir(path):
        inner = os.path.join(path, "Contents", "MacOS")
        names = sorted(os.listdir(inner)) if os.path.isdir(inner) else []
        if not names:
            fail("%s is a folder without a program in Contents/MacOS" % os.path.basename(path))
        return os.path.join(inner, names[0])
    return path


def clap_plugins(path):
    """The plugins of a .clap file: [(id, name)], read from its clap_entry (the plugin factory's
    descriptors). The library is loaded for that, as Carla loads it."""
    import ctypes

    class Version(ctypes.Structure):
        _fields_ = [("major", ctypes.c_uint32), ("minor", ctypes.c_uint32), ("revision", ctypes.c_uint32)]

    class Descriptor(ctypes.Structure):
        _fields_ = [("clap_version", Version), ("id", ctypes.c_char_p), ("name", ctypes.c_char_p), ("vendor", ctypes.c_char_p),
                    ("url", ctypes.c_char_p), ("manual_url", ctypes.c_char_p), ("support_url", ctypes.c_char_p),
                    ("version", ctypes.c_char_p), ("description", ctypes.c_char_p), ("features", ctypes.c_void_p)]

    class Factory(ctypes.Structure):
        pass

    Factory._fields_ = [("get_plugin_count", ctypes.CFUNCTYPE(ctypes.c_uint32, ctypes.POINTER(Factory))),
                        ("get_plugin_descriptor", ctypes.CFUNCTYPE(ctypes.POINTER(Descriptor), ctypes.POINTER(Factory), ctypes.c_uint32)),
                        ("create_plugin", ctypes.c_void_p)]

    class Entry(ctypes.Structure):
        _fields_ = [("clap_version", Version), ("init", ctypes.CFUNCTYPE(ctypes.c_bool, ctypes.c_char_p)),
                    ("deinit", ctypes.CFUNCTYPE(None)), ("get_factory", ctypes.CFUNCTYPE(ctypes.c_void_p, ctypes.c_char_p))]

    try:
        lib = ctypes.CDLL(clap_binary(path))
        entry = Entry.in_dll(lib, "clap_entry")
    except (OSError, ValueError) as e:
        fail("could not open %s as a CLAP plugin (%s). Is it a 64-bit CLAP for this computer?" % (os.path.basename(path), e))
    if not entry.init(clap_binary(path).encode("utf-8")):
        fail("%s did not start (its clap_entry init failed)" % os.path.basename(path))
    try:
        raw = entry.get_factory(b"clap.plugin-factory")
        if not raw:
            fail("%s has no plugin factory" % os.path.basename(path))
        factory = ctypes.cast(raw, ctypes.POINTER(Factory))
        out = []
        for i in range(factory.contents.get_plugin_count(factory)):
            d = factory.contents.get_plugin_descriptor(factory, i)
            if d:
                out.append(((d.contents.id or b"").decode("utf-8", "replace"), (d.contents.name or b"").decode("utf-8", "replace")))
        return out
    finally:
        entry.deinit()


def xml_text(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def clap_carla_plugin(path, cid, name):
    """A Carla project's <Plugin> for a CLAP plugin, as Carla writes it: its file and its id."""
    return ("<Plugin>\n  <Info>\n   <Type>CLAP</Type>\n   <Name>%s</Name>\n   <Binary>%s</Binary>\n   <Identifier>%s</Identifier>\n  </Info>\n"
            "  <Data>\n   <Active>Yes</Active>\n  </Data>\n </Plugin>") % (xml_text(name), xml_text(os.path.abspath(path)), xml_text(cid))


def clap_pick(path, wanted):
    """The (id, name) of the CLAP file's plugin to play: --clap_id's, else its first."""
    plugins = clap_plugins(path)
    if not plugins:
        fail("%s has no plugins in it" % os.path.basename(path))
    if wanted:
        for cid, name in plugins:
            if cid == wanted:
                return cid, name
        fail("%s has no plugin %s (it has %s)" % (os.path.basename(path), wanted, ", ".join(c for c, n in plugins)))
    return plugins[0]


def dll_info(path):
    """A Windows .dll's header: (its bits, 32 or 64, or None when it is not a Windows program file; the
    names it exports). A VST2 exports VSTPluginMain (or main), a VST3 GetPluginFactory."""
    try:
        with open(path, "rb") as f:
            data = f.read(64 * 1024 * 1024)
    except OSError:
        return None, set()
    if len(data) < 0x40 or data[:2] != b"MZ":
        return None, set()
    pe = struct.unpack_from("<I", data, 0x3C)[0]
    if pe + 24 > len(data) or data[pe:pe + 4] != b"PE\0\0":
        return None, set()
    machine = struct.unpack_from("<H", data, pe + 4)[0]
    sections = struct.unpack_from("<H", data, pe + 6)[0]
    opt_size = struct.unpack_from("<H", data, pe + 20)[0]
    opt = pe + 24
    magic = struct.unpack_from("<H", data, opt)[0]
    bits = 64 if magic == 0x20B else 32 if magic == 0x10B else None
    if machine in (0x8664, 0xAA64):
        bits = 64
    elif machine == 0x14C:
        bits = 32
    names = set()
    try:
        dirs = opt + (112 if magic == 0x20B else 96)
        exp_rva = struct.unpack_from("<I", data, dirs)[0]
        table = opt + opt_size

        def offset(rva):
            for i in range(sections):
                sec = table + 40 * i
                vsize, vaddr, rsize, raw = struct.unpack_from("<IIII", data, sec + 8)
                if vaddr <= rva < vaddr + max(vsize, rsize):
                    return rva - vaddr + raw
            return None
        at = offset(exp_rva) if exp_rva else None
        if at is not None:
            count, names_rva = struct.unpack_from("<I", data, at + 24)[0], struct.unpack_from("<I", data, at + 32)[0]
            first = offset(names_rva)
            for i in range(min(count, 5000)):
                o = offset(struct.unpack_from("<I", data, first + 4 * i)[0])
                if o is None:
                    continue
                end = data.find(b"\0", o)
                names.add(data[o:end].decode("latin-1"))
    except (struct.error, TypeError):
        pass
    return bits, names


def dll_problem(path):
    """Why a .dll will not play as it is: None when it is a plugin for this Python; "32" for a 32-bit VST2
    (Carla's bridge plays it); else what is wrong."""
    if not path.lower().endswith(".dll") or not os.path.isfile(path):
        return None
    bits, names = dll_info(path)
    if bits is None:
        return "%s is not a Windows plugin file" % os.path.basename(path)
    if names and not ({"VSTPluginMain", "main", "GetPluginFactory"} & names):
        return "%s is not a VST plugin: it has no VSTPluginMain (a VST2's start), so it is some other program's library" % os.path.basename(path)
    if bits == 32 and struct.calcsize("P") == 8:
        return "32"
    if bits == 64 and struct.calcsize("P") == 4:
        return "%s is a 64-bit plugin, but this Python is 32-bit: install 64-bit Python" % os.path.basename(path)
    return None


def lv2_bundle(path):
    """An LV2 plugin's bundle (a folder ending .lv2): the folder given, or the one a file given is in
    (avldrums.lv2's avldrums.dll); None for anything else."""
    if not path:
        return None
    p = os.path.normpath(os.path.abspath(path))
    if os.path.isdir(p) and p.lower().endswith(".lv2"):
        return p
    up = os.path.dirname(p)
    return up if up.lower().endswith(".lv2") and os.path.isdir(up) else None


def lv2_plugins(bundle):
    """The LV2 plugins in a bundle, [(uri, name)], from its .ttl files (x42 AVL Drumkits: a plugin a kit).
    A plugin is named in full (<http://...#BlackPearl>) or with a prefix (avldrums:BlackPearl, its
    @prefix avldrums: <http://...#>), as AVL Drumkits' files do."""
    text = ""
    for n in sorted(os.listdir(bundle)):
        if n.lower().endswith(".ttl"):
            text += open(os.path.join(bundle, n), encoding="utf-8", errors="replace").read() + "\n"
    prefixes = dict(re.findall(r"@prefix\s+([\w-]*):\s*<([^<>\s]*)>", text))
    out = []
    # A statement's subject (at a line's start), then "a" and its types, lv2:Plugin among them.
    for m in re.finditer(r"^\s*(?:<([^<>\s]+)>|([\w-]*):([\w-]+))\s+a\s+[^;.]*lv2:Plugin\b", text, re.M):
        if m.group(1):
            uri, names = m.group(1), [re.escape("<" + m.group(1) + ">")]
        elif m.group(2) in prefixes:
            uri = prefixes[m.group(2)] + m.group(3)
            names = [re.escape("<" + uri + ">"), r"(?<![\w:-])" + re.escape(m.group(2) + ":" + m.group(3)) + r"(?![\w-])"]
        else:
            continue
        if uri in [u for u, _ in out]:
            continue
        name = None
        for k in re.finditer("|".join(names), text):
            # The statement about it: up to its end (" ." at a line's end; its URIs have dots of their own).
            block = text[k.end():k.end() + 20000]
            end = re.search(r"\s\.\s*(\n|$)", block)
            nm = re.search(r"doap:name\s+\"([^\"]+)\"", block[:end.start()] if end else block)
            if nm:
                name = nm.group(1)
                break
        out.append((uri, name or uri.rsplit("#", 1)[-1].rsplit("/", 1)[-1]))
    return out


# The kit asked for (--clap_id: an LV2 plugin's URI, or part of its name: "Red Zeppelin").
LV2_WANTED = {"id": None}


def lv2_pick(bundle):
    """The (uri, name) of the bundle's plugin to play: --clap_id's, else its first."""
    plugins = lv2_plugins(bundle)
    if not plugins:
        fail("%s has no LV2 plugin in its .ttl files" % os.path.basename(bundle))
    wanted = (LV2_WANTED["id"] or "").strip().lower()
    if wanted:
        for uri, name in plugins:
            if wanted == uri.lower() or wanted in name.lower() or wanted in uri.lower():
                return uri, name
        fail("%s has no plugin %s; it has: %s" % (os.path.basename(bundle), LV2_WANTED["id"], "; ".join(n for _, n in plugins)))
    return plugins[0]


def lv2_carla_plugin(bundle):
    """A Carla project's <Plugin> for an LV2 plugin: its URI (Carla finds it in the usual LV2 folders)."""
    uri, name = lv2_pick(bundle)
    return ("<Plugin>\n  <Info>\n   <Type>LV2</Type>\n   <Name>%s</Name>\n   <URI>%s</URI>\n  </Info>\n"
            "  <Data>\n   <Active>Yes</Active>\n  </Data>\n </Plugin>") % (xml_text(name), xml_text(uri))


def rack_said(path):
    """The line saying how a plugin played in Carla's rack plays: an LV2's kit, or a 32-bit VST2 bridged."""
    if lv2_bundle(path):
        return "LV2 plugin: %s in Carla-Rack" % lv2_pick(lv2_bundle(path))[1]
    return "32-bit plugin: %s in Carla-Rack, through Carla's 32-bit bridge" % os.path.basename(path)


def bridged_carla_plugin(path):
    """A Carla project's <Plugin> for a VST2 .dll: Carla runs a 32-bit one in its 32-bit bridge. An LV2
    bundle's: its plugin by URI."""
    if lv2_bundle(path):
        return lv2_carla_plugin(lv2_bundle(path))
    return ("<Plugin>\n  <Info>\n   <Type>VST2</Type>\n   <Name>%s</Name>\n   <Binary>%s</Binary>\n   <UniqueID>0</UniqueID>\n  </Info>\n"
            "  <Data>\n   <Active>Yes</Active>\n  </Data>\n </Plugin>") % (xml_text(stem(path)), xml_text(os.path.abspath(path)))


def bridged_state(proc, path):
    """Puts the 32-bit VST2 into the Carla-Rack VST (bridged); returns the line saying so."""
    rack_load(proc, [bridged_carla_plugin(path)], ("Carla did not load %s (%%d of %%d). Is it in an LV2 folder Carla looks in (%s)?" % (
        os.path.basename(path), "; ".join(lv2_folders()) or "none found")) if lv2_bundle(path) else
        "Carla did not load %s (%%d of %%d). Is Carla's 32-bit bridge installed (carla-bridge-win32)?" % os.path.basename(path))
    return rack_said(path)


def bridged_project(path):
    """Where a 32-bit plugin's settings are kept: the Carla project Edit settings saves in Carla (~/.pulsekit/vst/<name>.carxp).
    An LV2's: its bundle's name and the kit asked for (avldrums-Red Zeppelin.carxp)."""
    bundle = lv2_bundle(path)
    if bundle:
        kit = re.sub(r"[^A-Za-z0-9 _-]", "", LV2_WANTED["id"] or "").strip()
        return os.path.join(os.path.expanduser("~"), ".pulsekit", "vst", stem(bundle)) + ("-" + kit if kit else "") + ".carxp"
    return os.path.splitext(state_home(path))[0] + ".carxp"


def carla_app(rack):
    """Carla's own program (Carla.exe on Windows): beside its rack plugin (the Carla.vst folder's parent), else in
    another Carla near it (Carla-2.5.10-win64\\Carla\\Carla.exe beside Carla-2.4.1-win64\\Carla.vst), in Pulsekit's
    plugin folders or Carla's usual places; None when there is none."""
    names = ("carla.exe", "carla")

    def look(folder, depth):
        try:
            items = sorted(os.listdir(folder))
        except OSError:
            return None
        for n in items:
            full = os.path.join(folder, n)
            if n.lower() in names and os.path.isfile(full):
                return full
        mac = os.path.join(folder, "Carla.app", "Contents", "MacOS", "Carla")
        if os.path.isfile(mac):
            return mac
        if depth <= 0:
            return None
        # The newest Carla first (Carla-2.5.10-win64 before Carla-2.4.1-win64).
        subs = [n for n in items if os.path.isdir(os.path.join(folder, n)) and not n.lower().endswith((".vst", ".vst3", ".clap", ".lv2"))]
        subs.sort(key=lambda n: [int(x) if x.isdigit() else 0 for x in re.findall(r"\d+", n)], reverse=True)
        for n in subs:
            hit = look(os.path.join(folder, n), depth - 1)
            if hit:
                return hit
        return None

    # Where Pulsekit was told it is (Files & locations: Carla program), tried first.
    try:
        kept = open(os.path.join(os.path.expanduser("~"), ".pulsekit", "carla-app.txt"), encoding="utf-8").read().strip()
        if kept and os.path.isfile(kept):
            return kept
    except OSError:
        pass
    here = os.path.dirname(os.path.abspath(rack)) if rack else ""
    tops = []
    for _ in range(3):
        if not here:
            break
        tops.append(here)
        up = os.path.dirname(here)
        if up == here:
            break
        here = up
    for k, top in enumerate(tops):
        hit = look(top, k + 1)
        if hit:
            return hit
    for d in pulsekit_plugin_folders() + [r"C:\Program Files\Carla", r"C:\Program Files (x86)\Carla", "/usr/bin", "/usr/local/bin", "/Applications/Carla.app/Contents/MacOS"]:
        hit = look(d, 2)
        if hit:
            return hit
    return None


def carla_edit(path, rack):
    """Edit settings for a 32-bit plugin: Carla's own program opens with it (its 32-bit bridge); the kit is set
    there and the project saved; returns the project, which later runs load into the rack."""
    import subprocess
    import time
    project = bridged_project(path)
    folder = os.path.dirname(project)
    if not os.path.isdir(folder):
        os.makedirs(folder)
    if not os.path.isfile(project) or "<Plugin>" not in open(project, encoding="utf-8", errors="replace").read():
        with open(project, "w", encoding="utf-8") as f:
            f.write("<?xml version='1.0' encoding='UTF-8'?>\n<!DOCTYPE CARLA-PROJECT>\n<CARLA-PROJECT VERSION='2.4'>\n "
                    + bridged_carla_plugin(path) + "\n</CARLA-PROJECT>\n")
    app = carla_app(rack)
    if not app:
        fail("Carla's program (Carla.exe) was not found beside %s: the window of a plugin Carla's rack plays (a 32-bit or LV2 one) opens in Carla "
             "(Files & locations: Carla program). Or open Carla, "
             "open the project %s (the plugin is in it), load the kit, save the project and close Carla; then Test."
             % (rack, project))
    print("Plugin: %s's window opens in Carla (%s): double-click %s in Carla's rack for its window, load a kit or patch, "
          "then save the project (Ctrl+S: %s) and close Carla." % (os.path.basename(path), app, stem(path), project))
    print("Plugin's window is open in Carla; press Stop timer in Pulsekit when the kit has loaded.")
    sys.stdout.flush()
    started = time.time()
    before = os.path.getmtime(project)
    timer_file = os.environ.get("PULSEKIT_TIMER_FILE")
    stopped = None
    try:
        proc = subprocess.Popen([app, os.path.abspath(project)], cwd=os.path.dirname(app))
    except OSError as e:
        fail("Carla (%s) did not start: %s" % (app, e))
    while proc.poll() is None:
        if stopped is None and timer_file and os.path.exists(timer_file):
            stopped = time.time()
            print("Timer stopped: %.1f s" % (stopped - started))
            sys.stdout.flush()
        time.sleep(0.2)
    took = (stopped or time.time()) - started
    if os.path.getmtime(project) == before:
        print("Note: the project was not saved in Carla (Ctrl+S before closing): the plugin plays as it starts, without the kit set there.")
    text = open(project, encoding="utf-8", errors="replace").read()
    if "<Plugin>" not in text:
        fail("the project %s has no plugin now: open %s in Carla, save the project with it and try again" % (project, os.path.basename(path)))
    with open(os.path.splitext(state_home(path))[0] + ".state.load", "w") as f:
        f.write("%.1f\n" % took)
    print("Wrote Plugin settings: %s (Carla project)" % project)
    print("Kit load time: %.1f s (%s): a test waits at most this long for the kit" % (took, "Stop timer" if stopped else "until Carla closed"))
    return project


def pulsekit_plugin_folders():
    """The plugin folders set in Pulsekit (vst_render's Params, Plugins tab: Plugin folders), that exist."""
    path = os.path.join(os.path.expanduser("~"), ".pulsekit", "plugin-folders.txt")
    out = []
    try:
        for line in open(path, encoding="utf-8", errors="replace"):
            if "=" in line:
                out += [p.strip() for p in line.split("=", 1)[1].split("|") if p.strip()]
    except OSError:
        pass
    return [d for d in out if os.path.isdir(d)]


def carla_rack():
    """Carla's Carla-Rack VST in the VST folders, Pulsekit's plugin folders and Carla's own folders; None when there is none."""
    dirs = vst_folders() + pulsekit_plugin_folders() + [d for d in ("/usr/lib/carla", "/usr/local/lib/carla", r"C:\Program Files\Carla",
                                                                    r"C:\Program Files (x86)\Carla", "/Applications/Carla.app/Contents/MacOS") if os.path.isdir(d)]
    # Where Pulsekit found it (it looks too, and keeps the path for vst_render): tried first.
    kept = os.path.join(os.path.expanduser("~"), ".pulsekit", "carla-rack.txt")
    try:
        found = open(kept, encoding="utf-8").read().strip()
        if found and os.path.exists(found):
            return found
    except OSError:
        pass
    for d in dirs:
        if is_carla_rack(d):
            return d
        for root, subdirs, files in os.walk(d):
            for n in sorted(subdirs + files):
                if is_carla_rack(n):
                    return os.path.join(root, n)
    return None


def is_carla_rack(path):
    """Carla's rack plugin by its name: CarlaRack.dll, Carla-Rack.vst3, or on Windows (Carla 2.4 and newer)
    CarlaVstShell.dll, the VST2 holding Carla-Rack (its first plugin) and Carla's others; not the effect ones
    (CarlaRackFX, CarlaVstFxShell)."""
    base = os.path.basename(os.path.normpath(path))
    if not base.lower().endswith(PLUGIN_ENDS):
        return False
    return re.sub(r"[^a-z]", "", os.path.splitext(base)[0].lower()) in ("carlarack", "carlavstshell")


def carla_searched():
    """The folders carla_rack looked through, for a "not found" message."""
    return vst_folders() + pulsekit_plugin_folders()


def carla_state(proc, project):
    """Loads the Carla project's plugins into the Carla-Rack VST; returns the line saying which."""
    import re
    if not os.path.isfile(project):
        fail("no Carla project at %s" % project)
    text = open(project, "rb").read().decode("utf-8", "replace")
    plugins = re.findall(r"<Plugin>.*?</Plugin>", text, re.S)
    if not plugins:
        fail("%s has no plugins (save the project in Carla with the plugin loaded)" % os.path.basename(project))
    names = []
    for block in plugins:
        info = re.search(r"<Info>.*?<Name>(.*?)</Name>", block, re.S)
        if info:
            names.append(info.group(1).strip())
    rack_load(proc, plugins, "Carla loaded %%d of the %%d plugins in %s. Are they installed on this computer (and in Carla's folders)?" % os.path.basename(project))
    return "Carla project: %s (%s)" % (os.path.basename(project), ", ".join(names))


def clap_state(proc, path, wanted):
    """Puts the CLAP plugin into the Carla-Rack VST; returns the line saying which."""
    cid, name = clap_pick(path, wanted)
    rack_load(proc, [clap_carla_plugin(path, cid, name)], "Carla did not load %s (%%d of %%d): it needs Carla 2.6 or newer for CLAP" % os.path.basename(path))
    return "CLAP plugin: %s (%s) in Carla-Rack" % (name or os.path.basename(path), cid)


def rack_plugins(proc):
    """How many plugins the Carla-Rack VST has now."""
    import tempfile
    check = os.path.join(tempfile.mkdtemp(prefix="pulsekit-carla-"), "check.state")
    proc.save_state(check)
    return open(check, "rb").read().count(b"<Plugin>")


def rack_load(proc, plugins, missing):
    """The plugins (Carla project <Plugin> blocks) into the Carla-Rack VST's state; `missing` says
    (with how many loaded, of how many) when Carla left some out."""
    import tempfile
    work = tempfile.mkdtemp(prefix="pulsekit-carla-")
    empty = os.path.join(work, "empty.state")
    proc.save_state(empty)
    raw = open(empty, "rb").read()
    at = raw.find(b"<?xml")
    end = raw.rfind(b"</CARLA-PROJECT>")
    if at < 4 or end < 0:
        fail("the plugin is not Carla's Carla-Rack (give Carla-Rack with --plugin, or leave --plugin out)")
    xml = raw[at:end] + ("\n".join(" " + p for p in plugins) + "\n</CARLA-PROJECT>\n").encode("utf-8") + b"\0"
    state = os.path.join(work, "project.state")
    # The rack's state is its project text, its length before it.
    with open(state, "wb") as f:
        f.write(raw[:at - 4] + struct.pack(">I", len(xml)) + xml)
    proc.load_state(state)
    # What the rack has now: a plugin Carla could not find is left out.
    loaded = rack_plugins(proc)
    if loaded < len(plugins):
        fail(missing % (loaded, len(plugins)))


def gizmo_map(kit, given):
    """The kit's MIDI map: the one given, else the kit folder's Midimap*.xml (a full one first)."""
    if given:
        if not os.path.isfile(given):
            fail("no MIDI map at %s" % given)
        return given
    folder = os.path.dirname(os.path.abspath(kit))
    maps = sorted(n for n in os.listdir(folder) if n.lower().startswith("midimap") and n.lower().endswith(".xml"))
    if not maps:
        fail("no Midimap*.xml in %s: give the kit's MIDI map with --midimap" % folder)
    full = [n for n in maps if "full" in n.lower()]
    return os.path.join(folder, (full or maps)[0])


def gizmo_side(name):
    """Where a kit channel goes in the stereo mix: -1 left (OHL, AmbL, Room_Left), 1 right, 0 the middle."""
    low = name.lower()
    if low.endswith("left") or low.endswith(("_l", "-l", " l")) or name.endswith("L"):
        return -1
    if low.endswith("right") or low.endswith(("_r", "-r", " r")) or name.endswith("R"):
        return 1
    return 0


def wav_samples(path):
    """A mono WAV DrumGizmo wrote, as floats: 32-bit float read from the disk as it is needed (it can be long)."""
    import numpy as np
    with open(path, "rb") as f:
        data = f.read(4096)
    p = 12
    fmt, bits = 3, 32
    while p + 8 <= len(data):
        cid = data[p:p + 4]
        size = struct.unpack("<I", data[p + 4:p + 8])[0]
        if cid == b"fmt ":
            fmt, _, _, _, _, bits = struct.unpack("<HHIIHH", data[p + 8:p + 24])
        elif cid == b"data":
            n = (os.path.getsize(path) - p - 8) // (bits // 8)
            if n <= 0:
                return np.zeros(0, dtype=np.float32)
            if fmt == 3 and bits == 32:
                return np.memmap(path, dtype="<f4", mode="r", offset=p + 8, shape=(n,))
            if bits == 16:
                return np.memmap(path, dtype="<i2", mode="r", offset=p + 8, shape=(n,)).astype(np.float32) / 32768.0
            break
        p += 8 + size + (size & 1)
    fail("%s: a WAV format DrumGizmo does not write" % os.path.basename(path))


def midi_file(path, notes, bpm=120, tpq=480):
    """A drum MIDI of (beat, note) hits, for the DrumGizmo check."""
    def vlq(v):
        out = [v & 0x7f]
        v >>= 7
        while v:
            out.insert(0, (v & 0x7f) | 0x80)
            v >>= 7
        return bytes(out)
    us = int(60000000 / bpm)
    ev = b"\x00\xff\x51\x03" + bytes([(us >> 16) & 0xff, (us >> 8) & 0xff, us & 0xff])
    at = 0
    for beat, note in notes:
        tick = int(beat * tpq)
        ev += vlq(tick - at) + bytes([0x99, note, 110]) + vlq(10) + bytes([0x89, note, 0])
        at = tick + 10
    ev += b"\x00\xff\x2f\x00"
    with open(path, "wb") as f:
        f.write(b"MThd" + struct.pack(">IHHH", 6, 0, 1, tpq) + b"MTrk" + struct.pack(">I", len(ev)) + ev)


class SoundFont:
    """A SoundFont (.sf2, .sfz, a zipped SFZ kit) as the plugin: Pulsekit's Midi2Wav (in DrumMidi_CRT.jar)
    plays each MIDI with it, its drum kit (bank 128, else its first preset); no DawDreamer or Carla."""

    note_map = None

    def __init__(self, rate, opts):
        self.rate = rate
        self.sf = opts["--plugin"]
        self.java = opts["--java"]
        self.jar = opts["--drummidi"]
        if not self.jar or not os.path.isfile(self.jar):
            fail("%s is a SoundFont: Pulsekit's Midi2Wav plays it, in DrumMidi_CRT.jar (--drummidi; Pulsekit gives its own, and Test does)"
                 % os.path.basename(self.sf))
        print("SoundFont: %s, played with Pulsekit's Midi2Wav (its drum kit: bank 128, else its first preset)" % os.path.basename(self.sf))

    def latency(self):
        return 0

    def render(self, np, midi=None, wav=None, tail=2.0, raw=False):
        import subprocess
        import tempfile
        if wav:
            raise ValueError("a SoundFont plays MIDI; --wav needs an effect plugin")
        if not raw:
            midi = sent_midi(midi, self.note_map)
        length = midi_notes(midi)[0]
        work = tempfile.mkdtemp(prefix="pulsekit-sf-")
        out = os.path.join(work, "out.wav")
        r = subprocess.run([self.java, "-cp", os.path.abspath(self.jar), "Midi2Wav", os.path.abspath(midi), out, "--sf2", os.path.abspath(self.sf),
                            "--rate", str(self.rate)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, cwd=work)
        if r.returncode != 0 or not os.path.isfile(out):
            said = r.stdout.decode("utf-8", "replace").strip().splitlines()
            raise ValueError("Midi2Wav: %s" % (said[-1] if said else "no WAV"))
        audio, _ = read_wav(out, self.rate)
        try:
            os.remove(out)
            os.rmdir(work)
        except OSError:
            pass
        frames = int((length + tail) * self.rate)
        if audio.shape[1] < frames:
            audio = np.hstack([audio, np.zeros((2, frames - audio.shape[1]), dtype=audio.dtype)])
        return audio[:, :frames], length, 0


def is_soundfont(path):
    """A SoundFont vst_render plays with Midi2Wav: .sf2, .sfz, or a zipped SFZ kit (kit.zip, kit.zip#folder/kit.sfz)."""
    low = (path or "").lower()
    return low.endswith((".sf2", ".sfz")) or ".zip#" in low or (low.endswith((".zip", ".tar.gz", ".tgz")) and os.path.isfile(path))


class Gizmo:
    """
    DrumGizmo's own program on a kit: each MIDI rendered, its channels mixed to stereo.

    Its MIDI file player puts each hit at the start of a block of about a thousand samples (up to
    23 ms early at 44.1 kHz), and some versions play the file at half speed. So the kit is rendered
    at four or eight times the rate (the blocks a few ms long) and every 4th or 8th sample kept; the
    speed is measured first, with one hit four seconds in.
    """

    def __init__(self, rate, opts):
        import shutil
        self.rate = rate
        self.kit = opts["--drumgizmo"]
        if not os.path.isfile(self.kit):
            fail("no DrumGizmo kit at %s (give its drumkit .xml)" % self.kit)
        self.exe = shutil.which("drumgizmo")
        if not self.exe:
            fail("DrumGizmo is not installed (Linux: sudo apt install drumgizmo). On Windows or Mac, give its VST with --plugin")
        self.midimap = gizmo_map(self.kit, opts["--midimap"])
        import re
        notes = [int(n) for n in re.findall(r'note\s*=\s*"(\d+)"', open(self.midimap, "rb").read().decode("utf-8", "replace"))]
        if not notes:
            fail("%s maps no notes" % os.path.basename(self.midimap))
        self.speed = 1.0
        self.speed = self.measure(notes[0])
        print("DrumGizmo: %s, MIDI map %s%s" % (self.kit, os.path.basename(self.midimap), "" if abs(self.speed - 1) < 0.01 else ", its MIDI speed %.3g corrected" % self.speed))

    def latency(self):
        return 0

    def run(self, midi, seconds, factor):
        """DrumGizmo on the MIDI at `factor` times the rate: its folder and channel files (name, path)."""
        import subprocess
        import tempfile
        work = tempfile.mkdtemp(prefix="pulsekit-gizmo-")
        cmd = [self.exe, "-i", "midifile", "-I", "file=%s,midimap=%s,speed=%.6f" % (os.path.abspath(midi), os.path.abspath(self.midimap), self.speed),
               "-o", "wavfile", "-O", "file=%s,srate=%d" % (os.path.join(work, "out"), self.rate * factor),
               "-e", str(int(seconds * self.rate * factor)), os.path.abspath(self.kit)]
        done = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        made = sorted(n for n in os.listdir(work) if n.startswith("out") and n.endswith(".wav"))
        if done.returncode != 0 or not made:
            last = done.stdout.decode("utf-8", "replace").strip().splitlines()[-3:]
            raise ValueError("DrumGizmo could not render it (%s)" % " / ".join(last))
        # outKick-0.wav: the channel's name, then its number.
        return work, [(n[3:-4].rsplit("-", 1)[0], os.path.join(work, n)) for n in made]

    @staticmethod
    def clean(work, files):
        for _, path in files:
            os.remove(path)
        os.rmdir(work)

    def measure(self, note):
        """DrumGizmo's MIDI speed: where a hit four seconds in (at 120 BPM) lands, against where it should."""
        import numpy as np
        import tempfile
        midi = os.path.join(tempfile.mkdtemp(prefix="pulsekit-gizmo-check-"), "check.mid")
        midi_file(midi, [(8, note)])
        try:
            work, files = self.run(midi, 9.5, 4)
        except ValueError as e:
            fail(str(e))
        first = None
        for _, path in files:
            audio = np.abs(np.asarray(wav_samples(path)))
            loud = np.nonzero(audio > max(1e-4, float(audio.max()) * 0.05))[0] if audio.size else []
            if len(loud) and (first is None or loud[0] < first):
                first = int(loud[0])
        self.clean(work, files)
        os.remove(midi)
        os.rmdir(os.path.dirname(midi))
        if first is None:
            fail("DrumGizmo played nothing for note %d of %s: does the MIDI map fit the kit?" % (note, os.path.basename(self.midimap)))
        landed = first / float(self.rate * 4)
        return round(landed / 4.0, 3) if landed > 1 else 1.0

    note_map = None

    def render(self, np, midi=None, wav=None, tail=2.0, raw=False):
        if wav:
            raise ValueError("DrumGizmo plays MIDI; --wav needs an effect plugin")
        if not raw:
            midi = sent_midi(midi, self.note_map)
        length = midi_notes(midi)[0]
        frames = int((length + tail) * self.rate)
        # Eight times the rate for a short MIDI (hits within 2 ms), four for a long one (5 ms): less disk.
        factor = 8 if length + tail <= 60 else 4
        work, files = self.run(midi, length + tail, factor)
        mix = np.zeros((2, frames), dtype=np.float32)
        for channel, path in files:
            audio = np.asarray(wav_samples(path)[::factor][:frames], dtype=np.float32)
            side = gizmo_side(channel)
            left, right = (1.0, 0.0) if side < 0 else (0.0, 1.0) if side > 0 else (0.7071, 0.7071)
            mix[0, :len(audio)] += audio * left
            mix[1, :len(audio)] += audio * right
        self.clean(work, files)
        return mix, length, 0


def scan():
    rack = carla_rack()
    print("Carla-Rack (for --carla): %s" % (rack or "not found; Carla is free at kx.studio/carla"))
    import shutil
    print("DrumGizmo (for --drumgizmo): %s" % (shutil.which("drumgizmo") or "not installed (Linux: apt install drumgizmo)"))
    for d in lv2_folders():
        bundles = sorted(n for n in os.listdir(d) if n.lower().endswith(".lv2"))
        print("%s (LV2, through --carla): %s" % (d, ", ".join(b[:-4] for b in bundles) or "none"))
    for d in clap_folders():
        print("%s (CLAP, through --clap):" % d)
        for root, subdirs, files in os.walk(d):
            for n in sorted(n for n in subdirs + files if n.lower().endswith(".clap")):
                print("  " + os.path.relpath(os.path.join(root, n), d))
    found = 0
    for d in vst_folders():
        print(d)
        for root, subdirs, files in os.walk(d):
            names = [n for n in subdirs + files if n.lower().endswith(PLUGIN_ENDS)]
            for n in sorted(names):
                print("  " + os.path.relpath(os.path.join(root, n), d))
                found += 1
            # A .vst3 or .vst bundle is one plugin: its insides are not listed.
            subdirs[:] = [s for s in subdirs if not s.lower().endswith(PLUGIN_ENDS)]
    if found == 0:
        print("No plugins in the usual VST folders. Give the plugin's file with --plugin.")
    else:
        print("Succeeded: %d plugin%s" % (found, "" if found == 1 else "s"))


def kit_tag(name, kit):
    """The plugin's name as the output files carry it, with the kit loaded in it (--kit_name):
    "Ugritone Drums (Finnish Metal 2000)"."""
    tag = stem(name)
    if kit and kit.strip():
        tag += " (%s)" % re.sub(r'[\\/:*?"<>|]+', "_", kit.strip())
    return tag


def map_file(text):
    """--map's file: as given, or a map file's bare name in ~/.pulsekit/maps or the maps folder beside
    this script (Pulsekit puts its own map files there); else the text as it is (pairs)."""
    if not text or os.path.isfile(text) or "=" in text:
        return text
    for d in (os.path.join(os.path.expanduser("~"), ".pulsekit", "maps"), os.path.join(os.path.dirname(os.path.abspath(__file__)), "maps")):
        f = os.path.join(d, text)
        if os.path.isfile(f):
            return f
    return text


def stem(path):
    base = os.path.basename(os.path.normpath(path))
    for end in PLUGIN_ENDS + (".mid", ".midi", ".wav", ".wave"):
        if base.lower().endswith(end):
            return base[:-len(end)]
    return os.path.splitext(base)[0]


def state_home(plugin):
    return os.path.join(os.path.expanduser("~"), ".pulsekit", "vst", stem(plugin) + ".state")


def var_len(d, p):
    v = 0
    while p < len(d):
        b = d[p]
        p += 1
        v = (v << 7) | (b & 0x7F)
        if b < 0x80:
            break
    return v, p


def retempo(path, bpm, folder):
    """A copy of the MIDI file at one tempo, bpm (its own tempo changes left out), in folder; its path."""
    d = open(path, "rb").read()
    if d[:4] != b"MThd":
        raise ValueError("%s is not a MIDI file" % os.path.basename(path))
    head = 8 + struct.unpack(">I", d[4:8])[0]
    out = bytearray(d[:head])
    us = int(round(60000000.0 / bpm))
    tempo = b"\x00\xff\x51\x03" + bytes([(us >> 16) & 0xFF, (us >> 8) & 0xFF, us & 0xFF])
    i, first = head, True
    while i + 8 <= len(d):
        kind, size = d[i:i + 4], struct.unpack(">I", d[i + 4:i + 8])[0]
        p, end = i + 8, min(len(d), i + 8 + size)
        if kind != b"MTrk":
            out += d[i:end]
            i += 8 + size
            continue
        body = bytearray(tempo if first else b"")
        first = False
        carry, status = 0, 0
        while p < end:
            delta, q = var_len(d, p)
            delta += carry
            b = d[q]
            if b == 0xFF:
                n, r = var_len(d, q + 2)
                event, after = d[q:r + n], r + n
                if d[q + 1] == 0x51:
                    # A tempo change: left out, its time kept for the next event.
                    carry, p = delta, after
                    continue
            elif b in (0xF0, 0xF7):
                n, r = var_len(d, q + 1)
                event, after = d[q:r + n], r + n
            else:
                if b & 0x80:
                    status = b
                    r = q + 1
                else:
                    # Running status: written out in full, as the event before it may be gone.
                    r = q
                hi = status & 0xF0
                after = r + (1 if hi in (0xC0, 0xD0) else 2)
                event = bytes([status]) + d[r:after]
            carry = 0
            body += write_var(delta) + event
            p = after
        out += b"MTrk" + struct.pack(">I", len(body)) + body
        i += 8 + size
    to = os.path.join(folder, os.path.basename(path))
    with open(to, "wb") as f:
        f.write(out)
    return to


def write_var(v):
    """A MIDI variable-length number."""
    b = [v & 0x7F]
    v >>= 7
    while v:
        b.insert(0, (v & 0x7F) | 0x80)
        v >>= 7
    return bytes(b)


def midi_notes(path):
    """The MIDI file's length (its last event) in seconds and its notes as (seconds, note), following tempo changes."""
    d = open(path, "rb").read()
    if d[:4] != b"MThd":
        raise ValueError("%s is not a MIDI file" % os.path.basename(path))
    ppq = struct.unpack(">H", d[12:14])[0]
    if ppq & 0x8000:
        raise ValueError("SMPTE-timed MIDI files are not supported")
    ppq = ppq or 480
    i = 8 + struct.unpack(">I", d[4:8])[0]
    events, tempos, ons = [], [], []
    while i + 8 <= len(d):
        kind, size = d[i:i + 4], struct.unpack(">I", d[i + 4:i + 8])[0]
        p, end, tick, status = i + 8, min(len(d), i + 8 + size), 0, 0
        while kind == b"MTrk" and p < end:
            delta, p = var_len(d, p)
            tick += delta
            b = d[p]
            if b == 0xFF:
                t = d[p + 1]
                n, p = var_len(d, p + 2)
                if t == 0x51 and n == 3:
                    tempos.append((tick, (d[p] << 16) | (d[p + 1] << 8) | d[p + 2]))
                p += n
                events.append(tick)
                continue
            if b in (0xF0, 0xF7):
                n, p = var_len(d, p + 1)
                p += n
                continue
            if b & 0x80:
                status = b
                p += 1
            hi = status & 0xF0
            if hi in (0xC0, 0xD0):
                p += 1
            else:
                if hi == 0x90 and p + 1 < len(d) and d[p + 1] > 0:
                    ons.append((tick, d[p]))
                p += 2
            events.append(tick)
        i += 8 + size
    tempos.sort()

    def seconds(tick):
        secs, at, us = 0.0, 0, 500000
        for t, v in tempos:
            if t >= tick:
                break
            secs += (t - at) * us / 1e6 / ppq
            at, us = t, v
        return secs + (tick - at) * us / 1e6 / ppq

    last = max(events) if events else 0
    return seconds(last), sorted((seconds(t), n) for t, n in ons)


# Drum families for --test, as Pulsekit's CompareHits groups them (hats, rides and crashes also together).
FAMILIES = (("Kick", (35, 36)), ("Snare", (37, 38, 39, 40)), ("Hats", (42, 44, 46)),
            ("Closed HH", (42, 44)), ("Open HH", (46,)), ("Ride", (51, 53, 59)), ("Crash", (49, 52, 55, 57)), ("Toms", (41, 43, 45, 47, 48, 50)),
            ("Cymbals", (42, 44, 46, 49, 51, 52, 53, 55, 57, 59)))
TOLERANCE = 0.03
TOMS = (41, 43, 45, 47, 48, 50)


def matched(truth, found, shift):
    """How many found hits are within TOLERANCE of a played hit (each played hit used once), with found shifted."""
    import bisect
    used, ok = set(), 0
    for x in found:
        x += shift
        j = bisect.bisect_left(truth, x - TOLERANCE)
        while j < len(truth) and truth[j] <= x + TOLERANCE:
            if j not in used:
                used.add(j)
                ok += 1
                break
            j += 1
    return ok


def score(played, found, end):
    """Per family (played, found, matched), after the best shift of DrumMidi's hits within 100 ms; and the shift."""
    played = [(t, n) for t, n in played if t < end]
    every_t = sorted(t for t, n in played)
    every_f = sorted(t for t, n in found)
    shift = max((matched(every_t, every_f, s / 1000.0), -abs(s), s / 1000.0) for s in range(-100, 101, 2))[2]
    rows = []
    for name, notes in FAMILIES:
        t = sorted(x for x, n in played if n in notes)
        f = sorted(x for x, n in found if n in notes)
        rows.append((name, len(t), len(f), matched(t, f, shift)))
    return rows, shift


class KeptLog:
    """--skip_existing's "Kept ... (already made)" lines: the first KEPT_SHOWN each, then a count
    every KEPT_EVERY (a run of hundreds kept would fill Output with them), and the total at the end."""

    def __init__(self, what, total, word="Kept", why="already made"):
        self.what, self.total, self.count, self.word, self.why = what, total, 0, word, why

    def kept(self, shown, why=None):
        self.count += 1
        if self.count <= KEPT_SHOWN:
            print("%s %s (%s)" % (self.word, shown, why or self.why))
        elif self.count % KEPT_EVERY == 0:
            print("%s %d of %d %s so far (%s)" % (self.word, self.count, self.total, self.what, self.why))

    def end(self):
        if self.count > KEPT_SHOWN:
            print("%s %d of %d %s (%s)" % (self.word, self.count, self.total, self.what, self.why))


KEPT_SHOWN = 5
KEPT_EVERY = 100


# The cymbal breakdown: each played cymbal hit, and what DrumMidi wrote there (within TOLERANCE, after
# the shift): the same kind, another one (a swap), or nothing (missed); and DrumMidi's cymbals where
# none was played (extra).
CYMBAL_KINDS = (("Closed HH", (42, 44)), ("Open HH", (46,)), ("Ride", (51, 53, 59)), ("Crash", (49, 52, 55, 57)))


def cymbal_kind(note):
    for k, (name, notes) in enumerate(CYMBAL_KINDS):
        if note in notes:
            return k
    return None


def cymbal_breakdown(played, found, end, shift):
    """A table: table[played kind][found kind, or len(CYMBAL_KINDS) for missed], and the extra hits of each
    found kind. The same kind is matched first, then the nearest other cymbal (each found hit used once)."""
    import bisect
    n = len(CYMBAL_KINDS)
    table = [[0] * (n + 1) for _ in range(n)]
    p = sorted((t, cymbal_kind(x)) for t, x in played if t < end and cymbal_kind(x) is not None)
    f = sorted((t + shift, cymbal_kind(x)) for t, x in found if cymbal_kind(x) is not None)
    times = [t for t, k in f]
    used = [False] * len(f)
    got = [None] * len(p)
    for same in (True, False):
        for i, (t, k) in enumerate(p):
            if got[i] is not None:
                continue
            j = bisect.bisect_left(times, t - TOLERANCE)
            best = None
            while j < len(f) and times[j] <= t + TOLERANCE:
                if not used[j] and (f[j][1] == k) == same and (best is None or abs(times[j] - t) < abs(times[best] - t)):
                    best = j
                j += 1
            if best is not None:
                used[best] = True
                got[i] = f[best][1]
    for i, (t, k) in enumerate(p):
        table[k][n if got[i] is None else got[i]] += 1
    extra = [0] * n
    for j, (t, k) in enumerate(f):
        if not used[j]:
            extra[k] += 1
    return table, extra


def swaps_line(table):
    """The swaps of a render, in short: "ride as closed HH 33, crash as ride 1" (the biggest first), or ""."""
    names = [k[0] for k in CYMBAL_KINDS]
    out = []
    for a in range(len(names)):
        for b in range(len(names)):
            if a != b and table[a][b]:
                out.append((table[a][b], "%s as %s %d" % (names[a], names[b], table[a][b])))
    out.sort(key=lambda x: -x[0])
    return ", ".join(s for _, s in out)


def breakdown_text(table, extra):
    """The All section's table: rows played, columns what DrumMidi wrote."""
    names = [k[0] for k in CYMBAL_KINDS]
    head = ["Played \\ DrumMidi wrote"] + names + ["missed", "right"]
    rows = [head]
    for a, name in enumerate(names):
        total = sum(table[a])
        rows.append([name] + [str(v) for v in table[a]] + ["%d%%" % round(100.0 * table[a][a] / total) if total else "-"])
    rows.append(["Not played (extra)"] + [str(v) for v in extra] + ["", ""])
    width = [max(len(r[c]) for r in rows) for c in range(len(head))]
    lines = ["Cymbal breakdown (each played cymbal hit, and what DrumMidi wrote there; right: the same kind):"]
    for r in rows:
        lines.append("  " + r[0].ljust(width[0]) + "  " + "  ".join(r[c].rjust(width[c]) for c in range(1, len(head))))
    return lines


# Pulsekit's reference kit profile (--compare_kits): kick and toms only, the average of five free
# DrumGizmo kits' learned profiles (ShittyKit, DRSKit, MuldjordKit, Aasimonster, CrocellKit).
REFERENCE_KIT = """# Pulsekit's reference kit profile: kick and toms, the average of five DrumGizmo kits'
36 0.2629 0.2734 0.2243 0.2393 0.2670 0.2118 0.2164 0.1115 0.0367 -0.0592 -0.1872 -0.2207 -0.2641 -0.3366 -0.3690 -0.4067
41 -0.4676 -0.0997 0.2021 0.4411 0.1960 0.0735 0.1065 0.2476 0.3217 0.1251 -0.0439 -0.1146 -0.2979 -0.1217 -0.1832 -0.3849
43 -0.5554 -0.2069 0.0093 0.2548 0.4733 0.1408 0.1691 0.2388 0.2390 0.0896 0.0483 -0.0890 -0.1670 -0.2001 -0.1442 -0.3004
45 -0.5633 -0.2476 -0.1553 -0.0776 0.2045 0.5062 0.3079 0.1779 0.0657 0.1890 0.0950 0.0589 -0.0301 -0.0998 -0.2872 -0.1444
47 -0.4886 -0.3354 -0.2247 -0.0109 0.1911 0.3224 0.3797 0.3002 0.1196 0.1549 0.1860 0.0611 -0.0168 -0.1478 -0.2913 -0.1995
48 -0.4335 -0.3512 -0.3638 -0.2315 -0.0514 0.2008 0.3829 0.3809 0.2542 0.1604 0.1530 0.0640 0.0973 0.0097 -0.0922 -0.1796
50 -0.1679 -0.1581 -0.2536 -0.3812 -0.1512 -0.1480 -0.2023 -0.0477 0.2199 -0.1344 -0.1770 0.2045 0.3892 0.3994 0.3864 0.2221
"""


def compare_profiles(where, own):
    """--compare_kits: the profiles DrumMidi runs with besides the test's own (`own`, or None): no profile
    (when the test has one), the reference (REFERENCE_KIT, written into a temporary file), and each other
    kit profile in `where` (vst-kit-<plugin>.kit from earlier tests). [(label, path or None)]."""
    import glob
    import tempfile
    out = [("no profile", None)] if own else []
    ref = os.path.join(tempfile.mkdtemp(prefix="vst-ref-"), "reference.kit")
    with open(ref, "w") as f:
        f.write(REFERENCE_KIT)
    out.append(("reference (DrumGizmo kits' average)", ref))
    for f in sorted(glob.glob(os.path.join(where, "vst-kit-*.kit"))):
        if own and os.path.abspath(f) == os.path.abspath(own):
            continue
        out.append((os.path.basename(f)[len("vst-kit-"):-len(".kit")], f))
    return out


def compare_text(own, rows):
    """The report's comparison: a line for each profile (the test's own first, `own`: its label and
    (total, cymbals)), the families DrumMidi tells apart by a profile (kick, toms) and how often each
    cymbal kind was written right. rows: [(label, total, cymbals)], total and cymbals as the test adds them."""
    lines = ["Kit profiles compared (--compare_kits; kick and toms are what a profile sorts, its cymbal lines the cymbals):"]
    for label, total, cym in [own] + list(rows):
        part = score_line([(f, total[f][0], total[f][1], total[f][2]) for f in ("Kick", "Toms") if f in total])
        right = []
        for a, (kind, _) in enumerate(CYMBAL_KINDS):
            n = sum(cym[a])
            right.append("%s %s" % (kind, "%d%%" % round(100.0 * cym[a][a] / n) if n else "-"))
        lines.append("  %s: %s | right: %s" % (label, part, ", ".join(right)))
    return lines


def score_line(rows):
    parts = []
    for name, played, found, ok in rows:
        if played == 0 and found == 0:
            continue
        parts.append("%s %d/%d P%3.0f R%3.0f" % (name, ok, played, 100.0 * ok / max(1, found), 100.0 * ok / max(1, played)))
    return "  ".join(parts)


MP3_RATES = (32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)


def to_mp3(java, jar, wav, mp3, kbps):
    """Wav2Mp3 (in DrumMidi_CRT.jar) on the WAV; None when the MP3 was made, else why not."""
    import subprocess
    try:
        r = subprocess.run([java, "-cp", os.path.abspath(jar), "Wav2Mp3", os.path.abspath(wav), os.path.abspath(mp3), "--bitrate", str(kbps)],
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=1800)
    except Exception as e:
        return "Wav2Mp3 did not run (%s)" % e
    said = r.stdout.decode("utf-8", "replace").strip().splitlines()
    failed = [l for l in said if l.startswith("Failed") or "Exception" in l or "Error" in l]
    if r.returncode != 0 or failed or not os.path.isfile(mp3) or os.path.getsize(mp3) == 0:
        return (failed or said[-1:] or ["no MP3 written"])[0]
    return None


def file_tempo(path):
    """The MIDI file's own (first) tempo in BPM, or None when it has none (it plays at 120)."""
    try:
        with open(path, "rb") as f:
            data = f.read()
    except OSError:
        return None
    i = data.find(b"\xff\x51\x03")
    if i < 0 or i + 6 > len(data):
        return None
    us = int.from_bytes(data[i + 3:i + 6], "big")
    return round(60000000.0 / us, 2) if us else None


def name_tempo(path, root=None):
    """--bpm_from_name: a tempo (40-300) in the MIDI's name, else in its folders' names (nearest first,
    up to `root`): a number with bpm after it first, else one standing alone (not S10, F4 or 7-4)."""
    names = [os.path.splitext(os.path.basename(path))[0]]
    d = os.path.dirname(os.path.abspath(path))
    top = os.path.abspath(root) if root else None
    while d and len(names) < 6:
        names.append(os.path.basename(d))
        if top and os.path.normcase(d) == os.path.normcase(top):
            break
        parent = os.path.dirname(d)
        if parent == d:
            break
        d = parent
    for n in names:
        for m in re.finditer(r"(\d{2,3})\s*bpm", n, re.I):
            v = int(m.group(1))
            if 40 <= v <= 300:
                return v
        for m in re.finditer(r"(?<![A-Za-z0-9.\-])(\d{2,3})(?![A-Za-z0-9.\-])", n):
            v = int(m.group(1))
            if 40 <= v <= 300:
                return v
    return None


def note_map(text, plugin=None):
    """--map: {note: note or None} from "45=46,41=none" or a text file of such pairs (# comments)."""
    return map_for(text, plugin)[0]


def map_key(name):
    """A plugin's or a map section's name to match on: letters and digits, lower case."""
    return re.sub(r"[^a-z0-9]", "", name.lower())


def default_map(plugin):
    """The map --test uses when none is given: Pulsekit's drum-kits.txt, when it has a section for
    the plugin (Anarchy, Riot, Ugritone/KVLT, Northern Artillery). (file, section), or (None, None)."""
    known = map_file("drum-kits.txt")
    if not os.path.isfile(known):
        return None, None
    try:
        found, part = map_for(known, plugin)
    except (ValueError, OSError):
        return None, None
    return (known, part) if part else (None, None)


def map_for(text, plugin=None):
    """--map's notes for the plugin, and which part of the map they came from.

    A text file can have a section per plugin ([Anarchy Drums] on a line, then its pairs): the
    section whose name is in the plugin's file name (or the other way round; case, spaces and
    signs aside) is used, over the lines before any section (for every plugin)."""
    section = None
    if os.path.isfile(text):
        common, parts, at = [], {}, None
        with open(text) as f:
            for line in f:
                line = line.split("#", 1)[0].strip()
                head = re.match(r"^\[(.+)\]$", line)
                if head:
                    at = head.group(1).strip()
                    parts.setdefault(at, [])
                elif line:
                    (parts[at] if at is not None else common).append(line)
        want = map_key(os.path.splitext(os.path.basename(os.path.normpath(plugin)))[0]) if plugin else ""
        pick = None
        for name in parts:
            k = map_key(name)
            if want and k and (k in want or want in k):
                pick = name
                break
        text = "\n".join(common + (parts[pick] if pick else []))
        if parts:
            section = pick or ""
    out = {}
    for pair in re.split(r"[,;\s]+", text.strip()):
        if not pair:
            continue
        if "=" not in pair:
            raise ValueError("%s is not note=note (45=46, or 41=none)" % pair)
        a, b = (x.strip() for x in pair.split("=", 1))
        try:
            src = note_number(a)
            dst = None if b.lower() in ("none", "-", "off", "") else note_number(b)
        except ValueError:
            raise ValueError("%s is not note=note (45=46, 36=%s, or 41=none)" % (pair, note_name(48)))
        if not 0 <= src <= 127 or dst is not None and not 0 <= dst <= 127:
            raise ValueError("%s: MIDI notes are 0 to 127" % pair)
        out[src] = dst
    return out, section


NOTE_LETTERS = ("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")


def note_octave():
    """How notes are named (Pulsekit's Files & locations: Note names): the octave of MIDI note 0. -1: note
    60 is C4 (most); -2: C3 (Yamaha, FL Studio, Cubase); 0: note 0 is C-0, 48 C-4 (DigiDrum)."""
    try:
        v = int(open(os.path.join(os.path.expanduser("~"), ".pulsekit", "note-names.txt"), encoding="utf-8").read().strip())
        return v if v in (-1, -2, 0) else -1
    except (OSError, ValueError):
        return -1


def note_name(n):
    """A MIDI note's name in the naming chosen: 48 is C3 (C4 = 60), C2 (C3 = 60) or C-4 (DigiDrum's)."""
    o = note_octave()
    return "%s%s%d" % (NOTE_LETTERS[n % 12], "-" if o >= 0 else "", n // 12 + o)


def note_number(text):
    """A note given as a number (48) or a name in the naming chosen (C-4, C#3, Db3); ValueError otherwise."""
    t = text.strip()
    if re.match(r"^\d+$", t):
        return int(t)
    m = re.match(r"^([A-Ga-g])([#b]?)(-?\d+)$", t)
    if not m:
        raise ValueError(t)
    o = note_octave()
    letter, sign, octave = m.group(1).upper(), m.group(2), m.group(3)
    # DigiDrum's naming writes its octave after a dash (C-4): a dash there is no minus.
    octave = int(octave.lstrip("-")) if o >= 0 else int(octave)
    n = NOTE_LETTERS.index(letter) + (1 if sign == "#" else -1 if sign == "b" else 0) + 12 * (octave - o)
    if not 0 <= n <= 127:
        raise ValueError(t)
    return n


def named_list(notes):
    """note_list with the notes' names: "48-51 (C-4 to D#-4)", "36 (C-3)"."""
    out, i = [], 0
    while i < len(notes):
        j = i
        while j + 1 < len(notes) and notes[j + 1] == notes[j] + 1:
            j += 1
        if j - i >= 2:
            out.append("%d-%d (%s to %s)" % (notes[i], notes[j], note_name(notes[i]), note_name(notes[j])))
        else:
            out += ["%d (%s)" % (n, note_name(n)) for n in notes[i:j + 1]]
        i = j + 1
    return ", ".join(out)


def note_map_home():
    """Pulsekit's note map (Files & locations: Edit): ~/.pulsekit/maps/map.txt."""
    return os.path.join(os.path.expanduser("~"), ".pulsekit", "maps", "map.txt")


# x42 AVL Drumkits (its LV2 plugin avldrums, or its kits' SoundFonts: Black_Pearl_4_LV2.sf2...): two toms
# only, 41/43 the floor tom (center, edge) and 45/47 the tom; its 48 is the swish hat and 50 Crash 1
# choked. Unless map.txt has a section for it, the songs' toms 48 and 50 go to its tom (edge, center).
AVL_KITS = ("blackpearl", "redzeppelin", "blondebop", "buskmansholiday")
AVL_NOTE_MAP = {48: 47, 50: 45}


def avl_kit(plugin):
    """Whether the plugin is AVL Drumkits: avldrums (its LV2 bundle), or a kit's SoundFont (Black_Pearl_4_LV2)."""
    low = re.sub(r"[^a-z0-9]", "", (plugin or "").lower())
    return low.startswith("avldrums") or ("lv2" in low and any(k in low for k in AVL_KITS))


def pad_map(given, plugin):
    """--note_map's notes for the plugin (the MIDI's note to the plugin's own), and a line saying where
    they came from; ({}, None) when there are none. Empty: map.txt's lines for the plugin, if any.
    AVL Drumkits without a section of its own: its built-in map (AVL_NOTE_MAP) under map.txt's lines."""
    text = given
    if not (text or "").strip():
        text = note_map_home()
    nmap, part = map_for(map_file(text), plugin) if os.path.isfile(map_file(text)) or (given or "").strip() else ({}, None)
    nmap = dict((a, b) for a, b in nmap.items() if a != b)
    built_in = avl_kit(plugin) and not part
    if built_in:
        nmap = dict(list(AVL_NOTE_MAP.items()) + list(nmap.items()))
    if not nmap:
        return {}, None
    where = os.path.basename(text) if os.path.isfile(map_file(text)) else "--note_map"
    if part:
        where += ", section [%s]" % part
    if built_in:
        where = "built in for AVL Drumkits (its 48 is the swish hat, 50 Crash 1 choked: the songs' toms there go to its tom; a [%s] section in map.txt replaces it)" % os.path.splitext(os.path.basename(os.path.normpath(plugin)))[0]
    pairs = ", ".join("%d=%s" % (a, "none" if b is None else "%d (%s)" % (b, note_name(b))) for a, b in sorted(nmap.items()))
    return nmap, "Note map: %s: %s (the MIDI's notes sent to the plugin's)" % (where, pairs)


def remap_midi(path, nmap, out):
    """The MIDI file with its notes changed as nmap says (note to note; to None: the note-on made silent);
    written to `out`, which is returned."""
    d = bytearray(open(path, "rb").read())
    if d[:4] != b"MThd":
        raise ValueError("%s is not a MIDI file" % os.path.basename(path))
    i = 8 + struct.unpack(">I", bytes(d[4:8]))[0]
    while i + 8 <= len(d):
        kind, size = bytes(d[i:i + 4]), struct.unpack(">I", bytes(d[i + 4:i + 8]))[0]
        p, end, status = i + 8, min(len(d), i + 8 + size), 0
        while kind == b"MTrk" and p < end:
            _, p = var_len(d, p)
            b = d[p]
            if b == 0xFF:
                n, p = var_len(d, p + 2)
                p += n
                continue
            if b in (0xF0, 0xF7):
                n, p = var_len(d, p + 1)
                p += n
                continue
            if b & 0x80:
                status = b
                p += 1
            hi = status & 0xF0
            if hi in (0xC0, 0xD0):
                p += 1
                continue
            if hi in (0x80, 0x90, 0xA0) and p + 1 < len(d) and d[p] in nmap:
                to = nmap[d[p]]
                if to is None:
                    if hi == 0x90:
                        d[p + 1] = 0
                else:
                    d[p] = to
            p += 2
        i += 8 + size
    with open(out, "wb") as f:
        f.write(bytes(d))
    return out


def sent_midi(midi, nmap):
    """The MIDI to play: as it is, or (a note map) a copy with its notes sent to the plugin's."""
    if not nmap or not midi:
        return midi
    import tempfile
    return remap_midi(midi, nmap, os.path.join(tempfile.mkdtemp(prefix="vst-notes-"), os.path.basename(midi)))


# The plugins played after --plugin, each with its own map and kit name.
MORE_PLUGINS = (("--plugin2", "--map2", "--kit_name2"), ("--plugin3", "--map3", "--kit_name3"), ("--plugin4", "--map4", "--kit_name4"))
MORE_SWITCHES = tuple(w for row in MORE_PLUGINS for w in row)


def drop_switch(argv, switch):
    """The arguments without `switch` and its value."""
    out, j = [], 0
    while j < len(argv):
        if argv[j] == switch:
            j += 2
            continue
        out.append(argv[j])
        j += 1
    return out


def plugin_runs(argv, opts):
    """--plugin2 to --plugin4: each plugin's own run, (plugin, its arguments): --plugin swapped for it,
    --state only for the first, and --map2 to --map4 as that plugin's --map (else --map for all)."""
    plugins = [(opts["--plugin"], None, None)] + [(opts[k], opts[m], opts.get(n)) for k, m, n in MORE_PLUGINS if opts[k]]
    runs = []
    for k, (plug, own_map, own_kit) in enumerate(plugins):
        one = []
        j = 0
        while j < len(argv):
            a = argv[j]
            if a in MORE_SWITCHES or (a == "--state" and k > 0) or (a == "--map" and own_map) \
                    or (a == "--kit_name" and k > 0):
                j += 2
                continue
            if a == "--plugin":
                one += [a, plug]
                j += 2
                continue
            one.append(a)
            j += 1
        if own_map:
            one += ["--map", own_map]
        if own_kit:
            one += ["--kit_name", own_kit]
        runs.append((plug, one))
    return runs


def lead_first(argv, opts):
    """No --plugin but one of --plugin2 to --plugin4 (Params' first row unticked): the first given leads
    (as --plugin, its own map as --map) and the others follow as --plugin2, --plugin3; without --dir, --dir2 is --dir.
    Returns the arguments so, with a line saying it, or None when nothing moves."""
    said = []
    out = list(argv)
    if not opts["--plugin"] and not opts["--carla"] and not opts["--drumgizmo"] and any(opts[k] for k, _, _ in MORE_PLUGINS):
        given = [(opts[k], opts[m], opts.get(n)) for k, m, n in MORE_PLUGINS if opts[k]]
        rest = []
        j = 0
        while j < len(out):
            if out[j] in MORE_SWITCHES + ("--map", "--kit_name"):
                j += 2
                continue
            rest.append(out[j])
            j += 1
        common = opts["--map"]
        lead, lead_map, lead_kit = given[0]
        rest += ["--plugin", lead] + (["--map", lead_map or common] if lead_map or common else []) + (["--kit_name", lead_kit] if lead_kit else [])
        for (k, m, n), (plug, own_map, own_kit) in zip(MORE_PLUGINS, given[1:]):
            rest += [k, plug] + ([m, own_map or common] if own_map or common else []) + ([n, own_kit] if own_kit else [])
        said.append("No --plugin: %s leads%s" % (os.path.basename(os.path.normpath(lead)),
                                                  ", %s after it" % ", ".join(os.path.basename(os.path.normpath(g[0])) for g in given[1:]) if len(given) > 1 else ""))
        out = rest
    if not opts["--dir"] and opts["--dir2"]:
        out = ["--dir" if a == "--dir2" else a for a in out]
        said.append("No --dir: %s is the folder" % opts["--dir2"])
    return (out, "; ".join(said)) if said else None


def mapped(notes, nmap):
    """The played notes as the plugin plays them (--map): moved to another note, or left out."""
    if not nmap:
        return notes
    return [(t, nmap.get(n, n)) for t, n in notes if nmap.get(n, n) is not None]


def silent_render(path, rate):
    """A render a run made before that is silent (a plugin with no kit then): a WAV with no sound in
    it, or an MP3 whose DrumMidi MIDI (from --test) found no hits. --skip_existing makes it again."""
    try:
        if path.lower().endswith(".mp3"):
            dm = os.path.splitext(path)[0] + "-dm.mid"
            return os.path.isfile(dm) and not midi_notes(dm)[1]
        import numpy as np
        audio, _ = read_wav(path, rate)
        return audio.size == 0 or float(np.max(np.abs(audio))) < 1e-4
    except Exception:
        return False


def whole_wav(path):
    """A WAV written to its end: sound in it, all of its frames in the file (not one cut off by a stop)."""
    try:
        if not os.path.isfile(path):
            return False
        w = wave.open(path, "rb")
        try:
            need = w.getnframes() * w.getnchannels() * w.getsampwidth()
        finally:
            w.close()
        return need > 0 and os.path.getsize(path) >= need + 44
    except Exception:
        return False


def drum_midi(java, jar, wav, mid, extra=(), said=None):
    """DrumMidi_CRT on the WAV (with `extra` switches); returns its notes, or an error text.
    `said`, a list: DrumMidi's own lines go in it."""
    import subprocess
    try:
        # DrumMidi_CRT writes into its working folder: run it in the WAV's.
        where = os.path.dirname(os.path.abspath(mid))
        # (A kit profile given by a path from here: in full, as DrumMidi runs there.)
        extra = list(extra)
        for i in range(len(extra) - 1):
            if extra[i] == "--kit":
                extra[i + 1] = os.path.abspath(extra[i + 1])
        r = subprocess.run([java, "-cp", os.path.abspath(jar), "DrumMidi_CRT", os.path.abspath(wav), os.path.basename(mid)] + list(extra),
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=1800, cwd=where)
    except Exception as e:
        return "DrumMidi_CRT did not run (%s)" % e
    lines = r.stdout.decode("utf-8", "replace").strip().splitlines()
    if said is not None:
        said.extend(lines)
    if r.returncode != 0 or not os.path.isfile(mid):
        tail = lines[-1:] or [""]
        return "DrumMidi_CRT failed (%s)" % tail[0]
    return midi_notes(mid)[1]


def read_wav(path, rate):
    import numpy as np
    w = wave.open(path, "rb")
    ch, width, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
    raw = w.readframes(n)
    w.close()
    if width == 2:
        a = np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0
    elif width == 3:
        b = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3).astype(np.int32)
        v = b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16)
        a = (np.where(v >= 1 << 23, v - (1 << 24), v) / float(1 << 23)).astype(np.float32)
    elif width == 4:
        a = np.frombuffer(raw, dtype="<i4").astype(np.float32) / 2147483648.0
    else:
        a = (np.frombuffer(raw, dtype=np.uint8).astype(np.float32) - 128) / 128.0
    a = a.reshape(-1, ch).T
    if sr != rate:
        # Linear resampling to the render's rate.
        m = int(round(a.shape[1] * rate / float(sr)))
        x = np.linspace(0, a.shape[1] - 1, m)
        a = np.stack([np.interp(x, np.arange(a.shape[1]), c) for c in a]).astype(np.float32)
    if a.shape[0] == 1:
        a = np.vstack([a, a])
    return a[:2], a.shape[1] / float(rate)


def write_wav(path, audio, rate, bits):
    import numpy as np
    a = np.clip(audio, -1.0, 1.0).T
    w = wave.open(path, "wb")
    w.setnchannels(a.shape[1])
    w.setframerate(rate)
    if bits == 24:
        v = np.round(a * 8388607.0).astype(np.int32).reshape(-1)
        b = np.empty((v.size, 3), dtype=np.uint8)
        b[:, 0] = v & 0xFF
        b[:, 1] = (v >> 8) & 0xFF
        b[:, 2] = (v >> 16) & 0xFF
        w.setsampwidth(3)
        w.writeframes(b.tobytes())
    else:
        w.setsampwidth(2)
        w.writeframes(np.round(a * 32767.0).astype("<i2").tobytes())
    w.close()


def apply_state(proc, plugin, given, label):
    """Loads the settings into the plugin; returns the line saying which."""
    path = given or state_home(plugin)
    if not given and not os.path.isfile(path):
        return "%s: the plugin's default settings" % label
    if not os.path.isfile(path):
        fail("no settings file %s" % path)
    low = path.lower()
    if low.endswith((".fxp", ".fxb")):
        ok = proc.load_preset(path)
    elif low.endswith(".vstpreset"):
        ok = proc.load_vst3_preset(path)
    else:
        try:
            proc.load_state(path)
            ok = True
        except RuntimeError as e:
            # DawDreamer opens the plugin's window to load its state: without a desktop (a server),
            # the parameter values saved beside it are set instead (not a sampler's kit or samples).
            values = path + ".params.json"
            if not os.path.isfile(values):
                fail("could not load the settings in %s (%s)" % (os.path.basename(path), e))
            proc.set_patch([(int(i), float(v)) for i, v in json.load(open(values))])
            return "%s: parameter values %s (Note: the full settings need a desktop: %s)" % (label, values, e)
    if ok is False:
        fail("the plugin did not take the settings in %s" % os.path.basename(path))
    return "%s: settings %s" % (label, path)


def edit(proc, plugin, given, label):
    """Opens the plugin's window; when it is closed, saves its settings. Returns the file saved."""
    path = given or state_home(plugin)
    if path.lower().endswith((".fxp", ".fxb", ".vstpreset")):
        path = os.path.splitext(path)[0] + ".state"
    print("%s: its window is open. Load a kit or patch; press Stop timer in Pulsekit when the kit has loaded "
          "(a test waits at most that long for it), then close the window." % label)
    sys.stdout.flush()
    # The kit's load time: from the window opening to Stop timer (Pulsekit's PULSEKIT_TIMER_FILE), or
    # to the window closing. A test waits at most this long for a slow sampler (KVLT) to have its kit.
    import threading
    import time
    started = time.time()
    timer = {"stopped": None, "open": True}
    timer_file = os.environ.get("PULSEKIT_TIMER_FILE")

    def watch():
        while timer["open"] and timer_file:
            if os.path.exists(timer_file):
                timer["stopped"] = time.time()
                print("Timer stopped: %.1f s" % (timer["stopped"] - started))
                sys.stdout.flush()
                return
            time.sleep(0.2)

    if timer_file:
        threading.Thread(target=watch, daemon=True).start()
    try:
        proc.open_editor()
    except Exception as e:
        fail("the plugin's window could not open (%s). --edit works on Windows and Mac." % e)
    finally:
        timer["open"] = False
    took = (timer["stopped"] or time.time()) - started
    folder = os.path.dirname(path)
    if folder and not os.path.isdir(folder):
        os.makedirs(folder)
    proc.save_state(path)
    # Its parameter values too, for a computer without a desktop (apply_state).
    with open(path + ".params.json", "w") as f:
        json.dump([[int(i), float(v)] for i, v in proc.get_patch()], f)
    print("Wrote %s settings: %s" % (label, path))
    with open(path + ".load", "w") as f:
        f.write("%.1f\n" % took)
    print("Kit load time: %.1f s (%s): a test waits at most this long for the kit" % (took, "Stop timer" if timer["stopped"] else "until the window closed"))
    return path


# Without a kit load time (Edit settings' timer), a test waits at most this long for a slow kit.
LOAD_WAIT = 30.0


def load_limit(plugin, given):
    """How long a test waits for the plugin's kit: the load time Edit settings measured (at least 5 s), else LOAD_WAIT."""
    path = given or state_home(plugin)
    if path.lower().endswith((".fxp", ".fxb", ".vstpreset")):
        path = os.path.splitext(path)[0] + ".state"
    try:
        with open(path + ".load") as f:
            return max(5.0, float(f.read().strip()))
    except (OSError, ValueError):
        return LOAD_WAIT


def load_plugin(engine, name, path):
    if not os.path.exists(path):
        fail("no plugin at %s" % path)
    try:
        return engine.make_plugin_processor(name, os.path.abspath(path))
    except Exception as e:
        fail("could not load %s (%s). Is it a 64-bit VST2 or VST3 plugin for this computer?" % (os.path.basename(path), e))


def collect(folder, recursive):
    """The MIDI files in the folder (and its subfolders), not DrumMidi's own (*-dm.mid) from a --test.
    A subfolder with audio and no MIDI (renders of an earlier run) is named once and passed by."""
    found = []
    for root, dirs, names in os.walk(folder):
        dirs.sort()
        midis = [n for n in sorted(names) if n.lower().endswith((".mid", ".midi")) and not n.lower().endswith("-dm.mid")]
        found.extend(os.path.join(root, n) for n in midis)
        audio = [n for n in names if n.lower().endswith(AUDIO_ENDS)]
        if root != folder and not midis and audio:
            print("Skipped %s: no MIDI files (%d audio file%s)" % (os.path.relpath(root, folder), len(audio), "" if len(audio) == 1 else "s"))
        if not recursive:
            break
    return found


AUDIO_ENDS = (".wav", ".wave", ".mp3", ".flac", ".ogg")


def sogni_zip(path):
    """Whether a zip is SogniMusic's (--zip): it holds the run's log, "SogniMusic log.txt" (in the first part)
    or, in a later part of a split one, DrumMidi's MIDI of it ("SogniMusic run N <model>.mid")."""
    try:
        with zipfile.ZipFile(path) as z:
            names = [os.path.basename(n) for n in z.namelist()]
    except (zipfile.BadZipFile, OSError):
        return False
    return "SogniMusic log.txt" in names or any(re.match(r"SogniMusic run \d+ .+\.midi?$", n) for n in names)


def zip_base(path):
    """A zip's name without ".zip" and a part number: sogni-Funk-d85a09ac-part1of2.zip -> sogni-Funk-d85a09ac."""
    return re.sub(r"-part\d+of\d+$", "", stem(path))


def unzip_midis(zips, into):
    """The MIDI files of SogniMusic's zips (each part of a split one) written into `into`; their paths."""
    os.makedirs(into, exist_ok=True)
    out = []
    for path in zips:
        with zipfile.ZipFile(path) as z:
            for n in z.namelist():
                base = os.path.basename(n)
                if not base.lower().endswith((".mid", ".midi")):
                    continue
                to = os.path.join(into, base)
                with z.open(n) as src, open(to, "wb") as dst:
                    dst.write(src.read())
                if to not in out:
                    out.append(to)
    return sorted(out)


def sogni_zips(folder, only=None, recursive=False):
    """SogniMusic's zips in the folder (with --recursive, in its subfolders too; or only the one named
    `only`, with its parts), unpacked: {"<base>-midi" folder beside them: its MIDI files}, the parts
    of a split zip into one folder. Each is said, with how many MIDI files it gave."""
    groups = {}
    for root, dirs, names in os.walk(folder):
        dirs.sort()
        for n in sorted(names):
            p = os.path.join(root, n)
            if only and zip_base(p) != only:
                continue
            if n.lower().endswith(".zip") and sogni_zip(p):
                groups.setdefault((root, zip_base(p)), []).append(p)
        if not recursive:
            break
    out = {}
    for (root, base), parts in sorted(groups.items()):
        into = os.path.join(root, base + "-midi")
        midis = unzip_midis(parts, into)
        print("SogniMusic zip %s%s: %d MIDI file%s into %s" % (os.path.relpath(os.path.join(root, base), folder), " (%d parts)" % len(parts) if len(parts) > 1 else "",
              len(midis), "" if len(midis) == 1 else "s", os.path.relpath(into, folder)))
        out[into] = midis
    return out


# The test beat's second part, for the kit profile (DrumMidi --learn-kit): the kick and each tom
# alone, KIT_HITS times, half a second apart; then the cymbals (closed hat, open hat, ride, crash)
# alone, KIT_HITS times, a second apart (they ring longer), soft, mid and hard (KIT_VELOCITIES: a
# song's hits are not all alike); then each cymbal KIT_WITH times struck with a kick and KIT_WITH
# times with a snare (a song's rides and hats are mostly on one): the profile learns how they look then.
# Last, the closed hat and the ride each played on in eighths, KIT_GROOVE_HITS of them, soft to hard
# (KIT_GROOVE_VELOCITIES): as a song plays them, each hit over the ring of the one before.
KIT_NOTES = (36, 41, 43, 45, 47, 48, 50)
KIT_CYMBALS = (42, 46, 51, 49)
KIT_HITS = 3
KIT_VELOCITIES = (70, 100, 120)
KIT_WITH = 2
KIT_COMPANIONS = ((36, "k"), (38, "s"))
KIT_GROOVE = (42, 51)
KIT_GROOVE_HITS = 16
KIT_GROOVE_VELOCITIES = (70, 120)


def kit_events():
    """Every note of the test beat's second part: (seconds, note, velocity, tag), at its 120 BPM. The tag
    is "" for a drum alone, "k" or "s" for a cymbal struck with a kick or a snare, "-" for that kick
    or snare (not learned), and "g" for a closed hat or ride played on in eighths."""
    out = [(2.0 + 0.5 * (i * KIT_HITS + k), n, 100, "") for i, n in enumerate(KIT_NOTES) for k in range(KIT_HITS)]
    start = 2.0 + 0.5 * len(KIT_NOTES) * KIT_HITS + 0.5
    out += [(start + 1.0 * (i * KIT_HITS + k), n, KIT_VELOCITIES[k % len(KIT_VELOCITIES)], "")
            for i, n in enumerate(KIT_CYMBALS) for k in range(KIT_HITS)]
    t = start + 1.0 * len(KIT_CYMBALS) * KIT_HITS
    for n in KIT_CYMBALS:
        for drum, tag in KIT_COMPANIONS:
            for _ in range(KIT_WITH):
                out += [(t, n, 100, tag), (t, drum, 100, "-")]
                t += 1.0
    lo, hi = KIT_GROOVE_VELOCITIES
    for n in KIT_GROOVE:
        for i in range(KIT_GROOVE_HITS):
            out.append((t + 0.25 * i, n, int(round(lo + (hi - lo) * i / float(KIT_GROOVE_HITS - 1))), "g"))
        t += 0.25 * KIT_GROOVE_HITS + 1.0
    return out


def kit_times():
    """The kick, toms and cymbals of the test beat, alone: (seconds, note), at its 120 BPM."""
    return [(t, n) for t, n, _, tag in kit_events() if tag == ""]


def check_midi(folder):
    """A test beat as a MIDI file in the folder: a bar at 120 BPM, kicks (36) and snares (38) on the
    beats, closed hats (42) on the eighths, on the drum channel; then the kick, each tom and each
    cymbal alone, each cymbal with a kick and with a snare, and the closed hat and ride played on in
    eighths (kit_events); returns its path."""
    events = []
    for k in range(8):
        tick = k * 240
        notes = [42] + ([36] if k in (0, 4) else [38] if k in (2, 6) else [])
        for n in notes:
            events.append((tick, 0x99, n, 100))
            events.append((tick + 120, 0x89, n, 0))
    for t, n, vel, _ in kit_events():
        tick = int(round(t * 960))
        events.append((tick, 0x99, n, vel))
        events.append((tick + 120, 0x89, n, 0))
    events.sort(key=lambda e: (e[0], e[1] == 0x99))
    data, last = bytearray(), 0
    for tick, status, note, vel in events:
        delta = tick - last
        last = tick
        var = [delta & 0x7F]
        while delta > 0x7F:
            delta >>= 7
            var.insert(0, (delta & 0x7F) | 0x80)
        data += bytes(var) + bytes([status, note, vel])
    data += b"\x00\xff\x2f\x00"
    path = os.path.join(folder, "vst-check.mid")
    with open(path, "wb") as f:
        f.write(b"MThd" + struct.pack(">IHHH", 6, 0, 1, 480) + b"MTrk" + struct.pack(">I", len(data)) + bytes(data))
    return path


# The warm-up before the test beat: each drum and cymbal a test or song plays, soft, mid and hard, into
# a render nobody hears. A sampler that loads a drum's sounds only when it is first played (KVLT's ride
# and crash stayed silent through a whole test beat, then played in the songs after it) has them then.
WARM_NOTES = (36, 38, 41, 43, 45, 47, 48, 42, 44, 46, 51, 53, 49, 57)
WARM_PAUSE = 2.0


def warm_midi(folder):
    """The warm-up as a MIDI file in the folder: WARM_NOTES, each at KIT_VELOCITIES, 0.15 s apart."""
    events, tick = [], 0
    for n in WARM_NOTES:
        for vel in KIT_VELOCITIES:
            events.append((tick, 0x99, n, vel))
            events.append((tick + 60, 0x89, n, 0))
            tick += 144
    events.sort(key=lambda e: (e[0], e[1] == 0x99))
    data, last = bytearray(), 0
    for tick, status, note, vel in events:
        delta = tick - last
        last = tick
        var = [delta & 0x7F]
        while delta > 0x7F:
            delta >>= 7
            var.insert(0, (delta & 0x7F) | 0x80)
        data += bytes(var) + bytes([status, note, vel])
    data += b"\x00\xff\x2f\x00"
    path = os.path.join(folder, "vst-warm.mid")
    with open(path, "wb") as f:
        f.write(b"MThd" + struct.pack(">IHHH", 6, 0, 1, 480) + b"MTrk" + struct.pack(">I", len(data)) + bytes(data))
    return path


def warm_up(chain, np):
    """The warm-up played through the chain and thrown away, then WARM_PAUSE seconds for the plugin to
    finish loading; True when it played."""
    import tempfile
    import time
    folder = tempfile.mkdtemp(prefix="vst-warm-")
    midi = warm_midi(folder)
    try:
        chain.render(np, midi=midi, tail=1.0)
    except ValueError:
        return False
    finally:
        try:
            os.remove(midi)
            os.rmdir(folder)
        except OSError:
            pass
    time.sleep(WARM_PAUSE)
    return True


def notes_midi(path, notes):
    """A MIDI file of (seconds, note, velocity) hits on the drum channel, at 120 BPM (960 ticks a second)."""
    events = []
    for t, n, vel in notes:
        tick = int(round(t * 960))
        events.append((tick, 0x99, n, vel))
        events.append((tick + 120, 0x89, n, 0))
    events.sort(key=lambda e: (e[0], e[1] == 0x99))
    data, last = bytearray(), 0
    for tick, status, note, vel in events:
        delta = tick - last
        last = tick
        var = [delta & 0x7F]
        while delta > 0x7F:
            delta >>= 7
            var.insert(0, (delta & 0x7F) | 0x80)
        data += bytes(var) + bytes([status, note, vel])
    data += b"\x00\xff\x2f\x00"
    with open(path, "wb") as f:
        f.write(b"MThd" + struct.pack(">IHHH", 6, 0, 1, 480) + b"MTrk" + struct.pack(">I", len(data)) + bytes(data))
    return path


def notes_heard(np, audio, rate, hits, window=0.25, rise=False):
    """Which notes of (seconds, note) hits sound: a hit is heard when its first `window` seconds are
    louder than 2% of the render's peak (and, with `rise`, at least half again as loud as the 50 ms
    before it: an earlier note still ringing is not this one). Returns (heard notes, silent notes), each sorted."""
    mono = np.max(np.abs(audio), axis=0) if audio.size else audio
    peak = float(np.max(mono)) if mono.size else 0.0
    heard, played = set(), set()
    for t, n in hits:
        played.add(n)
        a, b = int(t * rate), int((t + window) * rate)
        if peak > 1e-6 and a < mono.size and float(np.max(mono[a:max(b, a + 1)])) > peak * 0.02:
            before = float(np.max(mono[max(0, a - int(0.05 * rate)):a])) if a > 0 else 0.0
            if not rise or float(np.max(mono[a:max(b, a + 1)])) > before * 1.5:
                heard.add(n)
    return sorted(heard), sorted(played - heard)


def note_list(notes):
    """Notes as "36, 38, 41-51": runs of three or more written as a range."""
    out, i = [], 0
    while i < len(notes):
        j = i
        while j + 1 < len(notes) and notes[j + 1] == notes[j] + 1:
            j += 1
        out.append("%d-%d" % (notes[i], notes[j]) if j - i >= 2 else ", ".join(str(n) for n in notes[i:j + 1]))
        i = j + 1
    return ", ".join(out)


# The note sweep for a plugin silent for the GM drum notes: every note from SWEEP_LOW to SWEEP_HIGH alone.
SWEEP_LOW, SWEEP_HIGH, SWEEP_STEP = 24, 96, 0.3


def note_sweep(chain, np, rate):
    """The notes the plugin plays at all, of SWEEP_LOW to SWEEP_HIGH, each played alone (for a plugin
    whose pads are set to notes other than the GM drums'); an empty list when none sounds."""
    import tempfile
    folder = tempfile.mkdtemp(prefix="vst-sweep-")
    notes = list(range(SWEEP_LOW, SWEEP_HIGH + 1))
    hits = [(0.5 + SWEEP_STEP * i, n) for i, n in enumerate(notes)]
    print("Working: playing notes %d to %d one at a time, to find the plugin's pads" % (SWEEP_LOW, SWEEP_HIGH))
    sys.stdout.flush()
    midi = notes_midi(os.path.join(folder, "sweep.mid"), [(t, n, 110) for t, n in hits])
    try:
        audio, _, _ = chain.render(np, midi=midi, tail=1.0, raw=True)
    except ValueError:
        return []
    return notes_heard(np, audio, rate, hits, SWEEP_STEP * 0.8, rise=True)[0]


# The pads' sounds, each played alone this far apart (a cymbal rings on), at most this many of them.
PAD_GAP, PAD_MAX = 1.5, 40


def pad_features(np, audio, rate, t):
    """A pad's sound at t: where its spectrum sits (centroid, Hz), how much is low (<150 Hz) and high
    (>5 kHz), how noisy it is (flatness, 0 pure tone to 1 noise), its pitch (strongest peak under 1 kHz)
    and how long it rings (s, to -20 dB)."""
    mono = audio.mean(axis=0) if audio.ndim == 2 else audio
    a = int(t * rate)
    seg = mono[a:a + int(PAD_GAP * 0.9 * rate)]
    if seg.size < 4096 or float(np.max(np.abs(seg))) < 1e-6:
        return None
    # Its attack: from its loudest moment's start (a pad can speak a little late).
    blk = max(1, int(rate * 0.005))
    env = np.array([float(np.max(np.abs(seg[i:i + blk]))) for i in range(0, seg.size - blk, blk)])
    top = int(np.argmax(env))
    start = max(0, top - 2) * blk
    win = seg[start:start + 4096]
    if win.size < 4096:
        win = np.pad(win, (0, 4096 - win.size))
    power = np.abs(np.fft.rfft(win * np.hanning(4096))) ** 2 + 1e-18
    freqs = np.fft.rfftfreq(4096, 1.0 / rate)
    total = float(power.sum())
    under2k = float(power[freqs < 2000].sum() / total)
    band = (freqs >= 200) & (freqs <= 10000)
    flat = float(np.exp(np.mean(np.log(power[band]))) / np.mean(power[band]))
    low_band = (freqs >= 30) & (freqs <= 1000)
    peak = float(freqs[low_band][int(np.argmax(power[low_band]))])
    after = np.nonzero(env[top:] < env[top] * 0.1)[0]
    ring = (after[0] if after.size else env.size - top) * blk / float(rate)
    return {"centroid": float((freqs * power).sum() / total), "low": float(power[freqs < 150].sum() / total),
            "high": float(power[freqs > 5000].sum() / total), "under2k": under2k, "flat": flat, "pitch": peak, "ring": ring,
            "level": float(env[top])}


def pad_kind(f):
    """What a pad sounds like: kick, snare, hat (bright and short), cymbal (bright and long), tom (pitched,
    low to mid) or other (a cowbell, a zap)."""
    # A kick: low, its pitch under 130 Hz (a tom rings higher; suggest_map makes the higher of two "kicks" a tom).
    if f["low"] > 0.4 and f["centroid"] < 700 and f["pitch"] < 130:
        return "kick"
    # Hats and cymbals: nearly all above 2 kHz; a snare has its body (and its noise) lower too.
    if f["under2k"] < 0.15:
        return "hat" if f["ring"] < 0.35 else "cymbal"
    # A snare (or clap): noisy; or a little noisy with some highs and short (an 808 snare's tone and noise).
    if f["flat"] > 0.15 and f["centroid"] > 500 or f["flat"] > 0.08 and f["high"] > 0.05 and f["centroid"] > 800 and f["ring"] < 0.15:
        return "snare"
    if f["pitch"] < 500 and f["centroid"] < 2000:
        return "tom"
    return "other"


# The GM drum notes a suggested map sends somewhere, by what plays them (toms by height).
GM_ROLES = ((36, "kick"), (35, "kick"), (38, "snare"), (40, "snare"), (39, "snare2"), (37, "snare2"),
            (42, "closed"), (44, "closed"), (46, "open"), (49, "crash"), (57, "crash"), (52, "crash"), (55, "crash"),
            (51, "ride"), (59, "ride"), (53, "ride"))
GM_TOMS = (41, 43, 45, 47, 48, 50)


def pad_kinds(pads):
    """Each pad's kind (pad_kind); of several "kicks", those a quarter higher than the lowest are toms."""
    kinds = dict((n, pad_kind(f)) for n, f in pads.items())
    lows = sorted((pads[n]["pitch"], n) for n in pads if kinds[n] == "kick")
    for pitch, n in lows[1:]:
        if pitch > lows[0][0] * 1.25:
            kinds[n] = "tom"
    return kinds


def suggest_map(pads):
    """A note map from the pads' sounds ({note: features}): [(GM note, pad, why)]. A GM note that is a pad
    already playing its own kind of sound is left as it is; one landing on a pad of another kind with no
    pad of its own kind goes nowhere (none)."""
    kinds = pad_kinds(pads)
    of = lambda k: [n for n in sorted(pads) if kinds[n] == k]
    pick = {}
    kicks = sorted(of("kick"), key=lambda n: pads[n]["centroid"])
    if kicks:
        pick["kick"] = kicks[0]
    snares = sorted(of("snare"), key=lambda n: -pads[n]["flat"] * pads[n]["level"])
    if snares:
        pick["snare"] = snares[0]
        pick["snare2"] = snares[1] if len(snares) > 1 else snares[0]
    hats = sorted(of("hat"), key=lambda n: pads[n]["ring"])
    cymbals = sorted(of("cymbal"), key=lambda n: -pads[n]["ring"])
    if hats:
        pick["closed"] = hats[0]
        pick["open"] = hats[-1] if len(hats) > 1 else (cymbals[-1] if cymbals else None)
    elif cymbals:
        pick["closed"] = cymbals[-1]
    if cymbals:
        pick["crash"] = cymbals[0]
        pick["ride"] = cymbals[1] if len(cymbals) > 1 else cymbals[0]
    if not pick.get("open"):
        pick.pop("open", None)
    toms = sorted(of("tom"), key=lambda n: pads[n]["pitch"])
    want = [(note, pick.get(role), role) for note, role in GM_ROLES]
    for i, note in enumerate(GM_TOMS):
        tom = toms[int(round(i * (len(toms) - 1) / 5.0))] if toms else None
        want.append((note, tom, "tom"))
    out = []
    for note, pad, role in want:
        own = kinds.get(note)
        fits = own is not None and (own == role or role.startswith(own) or (own == "hat" and role in ("closed", "open"))
                                    or (own == "cymbal" and role in ("crash", "ride", "open")))
        if pad is None:
            if own is not None and not fits:
                out.append((note, None, "no %s found; %d plays a %s-like sound" % (role_word(role), note, own)))
            continue
        if pad == note or fits:
            continue
        f = pads[pad]
        out.append((note, pad, "%s-like (%s: %.0f Hz, rings %.2f s)" % (kinds[pad], role_word(role), f["pitch"] if kinds[pad] in ("kick", "tom") else f["centroid"], f["ring"])))
    return sorted(out)


def role_word(role):
    return {"kick": "kick", "snare": "snare", "snare2": "clap or rim", "closed": "closed hat", "open": "open hat",
            "crash": "crash", "ride": "ride", "tom": "tom"}[role]


def pad_sounds(chain, np, rate, notes):
    """Each sounding pad played alone, PAD_GAP s apart: {note: pad_features}."""
    import tempfile
    notes = notes[:PAD_MAX]
    hits = [(0.5 + PAD_GAP * i, n) for i, n in enumerate(notes)]
    print("Working: playing each of its %d pads alone, to hear what each one is" % len(notes))
    sys.stdout.flush()
    midi = notes_midi(os.path.join(tempfile.mkdtemp(prefix="vst-pads-"), "pads.mid"), [(t, n, 110) for t, n in hits])
    try:
        audio, _, _ = chain.render(np, midi=midi, tail=1.5, raw=True)
    except ValueError:
        return {}
    out = {}
    for t, n in hits:
        f = pad_features(np, audio, rate, t)
        if f is not None:
            out[n] = f
    return out


# The drums a kit must have: a test beat silent at any of them looks for them on the plugin's other notes.
KEY_NOTES = (36, 38, 42, 46, 49, 51)


def print_suggestion(chain, np, rate, plugin, plays=None, only=None):
    """Test's suggested note map for a plugin whose pads are on other notes: printed as "Map suggestion:
    [Plugin] 36=48 # why" lines (Pulsekit offers to add them to map.txt); how many. `only`: just these
    notes (the ones silent in the test beat; the others play already)."""
    if plays is None:
        plays = note_sweep(chain, np, rate)
    pads = pad_sounds(chain, np, rate, plays)
    lines = [l for l in suggest_map(pads) if only is None or (l[0] in only and l[1] is not None)]
    if not lines:
        return 0
    section = stem(plugin)
    print("Pads: " + "; ".join("%d (%s) %s" % (n, note_name(n), pad_kinds(pads)[n]) for n in sorted(pads)))
    for note, pad, why in lines:
        print("Map suggestion: [%s] %d=%s # %s" % (section, note, "none" if pad is None else pad, why if pad is None else "%s %s" % (note_name(pad), why)))
    return len(lines)


def beat_shape(np, audio, rate):
    """The test beat's loudness every 50 ms (0..1): what a kit sounds like, to tell two kits apart."""
    mono = np.max(np.abs(audio), axis=0) if audio.ndim == 2 else np.abs(audio)
    blk = int(rate * 0.05)
    n = mono.size // blk
    env = mono[:n * blk].reshape(n, blk).max(axis=1)
    top = float(env.max()) if n else 0.0
    return [round(float(v) / top, 3) for v in env] if top > 0 else []


def same_kit_check(np, audio, rate, plugin, kit):
    """Whether this kit's test beat sounds as another kit's did through this plugin (the kit not changed in
    the plugin, only its name): kept in ~/.pulsekit/vst/<plugin>.sounds.json; the line saying so, or None."""
    shape = beat_shape(np, audio, rate)
    if not shape or not kit:
        return None
    path = os.path.join(os.path.expanduser("~"), ".pulsekit", "vst", stem(plugin) + ".sounds.json")
    try:
        kept = json.load(open(path, encoding="utf-8"))
    except (OSError, ValueError):
        kept = {}
    same = []
    for other, was in sorted(kept.items()):
        if other == kit or abs(len(was) - len(shape)) > 1:
            continue
        n = min(len(was), len(shape))
        a, b = np.array(shape[:n]), np.array(was[:n])
        if float(np.mean(np.abs(a - b))) < 0.01 and float(np.corrcoef(a, b)[0, 1]) > 0.995:
            same.append(other)
    kept[kit] = shape
    try:
        folder = os.path.dirname(path)
        if not os.path.isdir(folder):
            os.makedirs(folder)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(kept, f)
    except OSError:
        pass
    if not same:
        return None
    return ("Kit check: the test beat sounds exactly as it did for %s: was the kit really changed in the plugin? "
            "(Edit settings: pick the kit and save; in Carla, tick Use Chunks in the plugin's settings so a kit picked "
            "in its own window is saved)" % ", ".join("\"%s\"" % k for k in same))


# The choke beat: each cymbal and its choke alone, this far apart, this many times each.
CHOKE_GAP, CHOKE_HITS = 1.5, 3


CHOKE_PRIVATE = 100
CHOKE_DRUMS = (35, 36, 37, 38, 39, 40, 41, 43, 45, 47, 48, 50)


def chokes_text(pairs):
    return ",".join("%d=%d" % kv for kv in sorted(pairs.items()))


def choke_beat(chain, np, rate, bits, opts, kit_file, pairs):
    """--chokes: each cymbal (a pair's first note) and its choke (the second) played alone, CHOKE_HITS
    times each, through the plugin (its notes as they are: no note map), then DrumMidi with --chokes and
    the kit profile on the render. Returns the lines saying what each was written as."""
    import tempfile
    pairs = dict((a, b) for a, b in pairs.items() if b is not None)
    if not pairs:
        return ["Chokes: no choke notes in --chokes"]
    hits = []
    t = 0.5
    for plain, choked in sorted(pairs.items()):
        for n in [plain] * CHOKE_HITS + [choked] * CHOKE_HITS:
            hits.append((t, n))
            t += CHOKE_GAP
    work = tempfile.mkdtemp(prefix="vst-chokes-")
    midi = notes_midi(os.path.join(work, "chokes.mid"), [(a, n, 110) for a, n in hits])
    print("Working: playing each cymbal and its choke alone (--chokes %s)" % ", ".join("%d=%d" % kv for kv in sorted(pairs.items())))
    sys.stdout.flush()
    try:
        audio, _, _ = chain.render(np, midi=midi, tail=1.5, raw=True)
    except ValueError as e:
        return ["Chokes: the choke beat did not play (%s)" % e]
    audio, _ = finish(np, audio, 100)
    wav = os.path.join(work, "chokes.wav")
    write_wav(wav, audio, rate, bits)
    said = []
    # DrumMidi writes the chokes on notes of their own (100 on), so a choke note that is a drum's too (AVL's
    # 50, Crash 1 choked, is the songs' high tom) is told apart; drums written with a cymbal are left out
    # (a sampled kit's cymbal carries the room's low end: alone, it can pass for a kick or a tom).
    private = dict((plain, CHOKE_PRIVATE + i) for i, plain in enumerate(sorted(pairs)))
    found = drum_midi(opts["--java"], opts["--drummidi"], wav, os.path.join(work, "chokes-dm.mid"),
                      ["--chokes", ",".join("%d=%d" % kv for kv in sorted(private.items()))] + (["--kit", kit_file] if kit_file else []), said)
    if isinstance(found, str):
        return ["Chokes: %s" % found]
    back = dict((v, pairs[k]) for k, v in private.items())
    found = [(u, back.get(k, k)) for u, k in found if k in back or k not in CHOKE_DRUMS]
    said = [l.replace(chokes_text(private), chokes_text(pairs)) for l in said]
    for k, v in private.items():
        said = [re.sub(r"\b%d as %d\b" % (k, v), "%d as %d" % (k, pairs[k]), l) for l in said]
    out = []
    for plain, choked in sorted(pairs.items()):
        for n, what in ((plain, "%d plain" % plain), (choked, "%d (%d choked)" % (choked, plain))):
            written = {}
            for a, m in hits:
                if m != n:
                    continue
                got = sorted(set(k for u, k in found if -0.06 < u - a < 0.2))  # (a sampled crash swells: found up to ~150 ms on)
                key = "+".join(str(k) for k in got) or "missed"
                written[key] = written.get(key, 0) + 1
            out.append("Chokes: %s written as %s" % (what, ", ".join("%s %d of %d" % (k, c, CHOKE_HITS) for k, c in sorted(written.items()))))
    return out + [l.strip() for l in said if l.startswith("Chokes")]


def kit_missing(said):
    """The kick, toms and cymbals DrumMidi --learn-kit heard nothing (or next to nothing) of: how many
    (99 when it learned no tom, or failed)."""
    if not said.startswith("Wrote ") or "no tom played" in said:
        return 99
    n = 0
    for part in said.split(";"):
        part = part.strip()
        if part.startswith("silent in this kit:") or part.startswith("nothing to read:"):
            n += len(re.findall(r"\b\d+ \(", part))
        elif part.startswith("cymbals not heard:"):
            # "51 ride (0.0% of the loudest cymbal), 49 crash (no sound at its hits)"
            n += len(re.findall(r"\b\d+ [a-z ]+\(", part))
    return n


def kit_times_text(nmap):
    """The test beat's notes DrumMidi --learn-kit learns the kit from, as the plugin plays them (--map:
    a note played as another drum, or not at all, is left out): "seconds note" lines, then k or s for a
    cymbal struck with a kick or a snare, g for one played on in eighths."""
    lines = []
    for t, n, _, tag in kit_events():
        m = nmap.get(n, n)
        if tag == "-" or m is None:
            continue
        if tag == "" and (m == 36 or m in TOMS or (n in KIT_CYMBALS and m in KIT_CYMBALS)):
            lines.append("%.4f %d\n" % (t, m))
        elif tag and n in KIT_CYMBALS and m in KIT_CYMBALS:
            # A cymbal struck with a kick ("k") or a snare ("s"): learned apart, as it sounds then.
            lines.append("%.4f %d %s\n" % (t, m, tag))
    return "".join(lines)


def learn_kit(java, jar, audio, rate, bits, nmap, out):
    """DrumMidi --learn-kit on the test beat's render: the kit's kick and toms as the plugin plays them
    (--map: a note played as another drum, or not at all, is not learned), into `out`. Returns
    DrumMidi's line, or why not."""
    import subprocess
    import tempfile
    work = tempfile.mkdtemp(prefix="vst-kit-")
    wav = os.path.join(work, "check.wav")
    write_wav(wav, audio, rate, bits)
    times = os.path.join(work, "check-times.txt")
    with open(times, "w") as f:
        f.write(kit_times_text(nmap))
    try:
        r = subprocess.run([java, "-cp", os.path.abspath(jar), "DrumMidi_CRT", "--learn-kit", wav, times, os.path.abspath(out)],
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=600)
    except Exception as e:
        return "DrumMidi_CRT did not run (%s)" % e
    lines = r.stdout.decode("utf-8", "replace").strip().splitlines()
    return lines[-1] if lines else "DrumMidi_CRT said nothing"


def cut_hits(java, jar, wav, times, out):
    """DrumMidi --cut-hits: the test beat's hits as WAVs of their own in `out`, with their index (hits.txt)
    for Pulsekit's drum sound library. Returns DrumMidi's line, or why not."""
    import subprocess
    try:
        r = subprocess.run([java, "-cp", os.path.abspath(jar), "DrumMidi_CRT", "--cut-hits", os.path.abspath(wav), os.path.abspath(times), out],
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=600)
    except Exception as e:
        return "DrumMidi_CRT did not run (%s)" % e
    lines = [l for l in r.stdout.decode("utf-8", "replace").strip().splitlines() if l.strip()]
    return lines[-1] if lines else "DrumMidi_CRT said nothing"


def silent_note(note, gizmo, carla):
    """finish()'s silent note, for DrumGizmo (its MIDI map) or Carla (its project's kit)."""
    if note and gizmo and note.startswith("the render is silent"):
        return "the render is silent. Does the MIDI map play this kit's drums with the MIDI's notes (--midimap)?"
    if note and carla and note.startswith("the render is silent"):
        return ("the render is silent. Does the plugin need a kit loaded? Load it in Carla and save the project again (for a 32-bit plugin: "
                "Edit settings opens it in Carla). Is Carla's 32-bit bridge (carla-bridge-win32.exe) beside Carla?")
    return note


# A run stops when its first renders are all silent: the plugin plays nothing with these settings.
SILENT_STOP = 3


def pls_midis(path):
    """A playlist's (.pls) MIDI files, in its order; a name without a folder is beside it."""
    found = []
    beside = os.path.dirname(os.path.abspath(path))
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            t = line.strip()
            m = re.match(r"(?i)file\d+=(.+)", t)
            if not m:
                continue
            name = m.group(1).strip()
            full = name if os.path.isabs(name) else os.path.join(beside, name)
            if not os.path.isfile(full):
                print("Not found: %s" % name)
            elif name.lower().endswith((".mid", ".midi")) and not name.lower().endswith("-dm.mid"):
                found.append(full)
    return found


class Chain(object):
    """The plugin (and the effect) loaded once, with their settings, for every file of a run."""

    def __init__(self, daw, rate, opts, switches):
        self.engine = daw.RenderEngine(rate, 512)
        self.rate = rate
        self.plugin = opts["--plugin"]
        self.main = load_plugin(self.engine, "plugin", self.plugin)
        print("Plugin: %s (%d in, %d out)" % (os.path.basename(os.path.normpath(self.plugin)), self.main.get_num_input_channels(), self.main.get_num_output_channels()))
        clap = opts.get("--clap") or opts.get("--bridged")
        put = (lambda: bridged_state(self.main, clap)) if opts.get("--bridged") else (lambda: clap_state(self.main, clap, opts.get("--clap_id")))
        bridged = opts.get("--bridged")
        if opts["--carla"]:
            print(carla_state(self.main, opts["--carla"]))
        elif bridged and "--edit" in switches:
            # A 32-bit plugin's window: in Carla's own program (the rack plugin has no window of its own on Windows).
            print(carla_state(self.main, carla_edit(bridged, self.plugin)))
            print(rack_said(bridged))
        elif bridged and os.path.isfile(bridged_project(bridged)):
            # Its settings: the Carla project saved in Edit settings (its kit with it).
            print(carla_state(self.main, bridged_project(bridged)))
            print(rack_said(bridged))
        elif clap and "--edit" in switches:
            # The rack with the plugin in it; its settings are saved under the CLAP's name.
            print(put())
            print("In the rack: double-click the plugin for its own window.")
            edit(self.main, clap, opts["--state"], "Plugin")
        elif clap:
            saved = opts["--state"] or state_home(clap)
            if os.path.isfile(saved):
                print(apply_state(self.main, clap, opts["--state"], "Plugin"))
                if rack_plugins(self.main) == 0:
                    print(put())
            else:
                print(put())
        elif "--edit" in switches:
            edit(self.main, self.plugin, opts["--state"], "Plugin")
        else:
            print(apply_state(self.main, self.plugin, opts["--state"], "Plugin"))
        # How long a test waits for a slow kit (the load time Edit settings measured).
        self.load_limit = load_limit(clap or self.plugin, opts["--state"])
        self.fx = None
        effect = opts["--effect"]
        if effect:
            self.fx = load_plugin(self.engine, "effect", effect)
            print("Effect: %s" % os.path.basename(os.path.normpath(effect)))
            if "--edit" in switches:
                edit(self.fx, effect, opts["--effect_state"], "Effect")
            else:
                print(apply_state(self.fx, effect, opts["--effect_state"], "Effect"))

    def latency(self):
        n = self.main.get_latency_samples()
        if self.fx is not None:
            n += self.fx.get_latency_samples()
        return max(0, int(n))

    note_map = None

    def render(self, np, midi=None, wav=None, tail=2.0, raw=False):
        """The audio (2 x samples) of the MIDI or the WAV through the chain, its latency taken off; and its length.
        The note map (--note_map) changes the notes the plugin gets, unless `raw` (the note sweep)."""
        if not raw:
            midi = sent_midi(midi, self.note_map)
        graph = []
        if wav:
            data, length = read_wav(wav, self.rate)
            graph.append((self.engine.make_playback_processor("input", data), []))
            graph.append((self.main, ["input"]))
        else:
            length = midi_notes(midi)[0]
            if not self.main.load_midi(os.path.abspath(midi), clear_previous=True, beats=False, all_events=True):
                raise ValueError("the plugin could not take %s" % os.path.basename(midi))
            graph.append((self.main, []))
        if self.fx is not None:
            graph.append((self.fx, ["plugin"]))
        if not self.engine.load_graph(graph):
            raise ValueError("the plugins could not be connected")
        late = self.latency()
        self.engine.render(length + tail + late / float(self.rate))
        audio = np.asarray(self.engine.get_audio(), dtype=np.float32)
        if audio.ndim == 1:
            audio = audio[None, :]
        if audio.shape[0] == 1:
            audio = np.vstack([audio, audio])
        if audio.shape[0] > 2:
            # A drum plugin with an output for each drum (DrumGizmo's VST): the others go in the middle.
            rest = audio[2:].sum(axis=0) * 0.7071
            audio = np.vstack([audio[0] + rest, audio[1] + rest])
        # The plugins' own delay (lookahead, oversampling): the hits land where the MIDI has them.
        return audio[:2, late:], length, late


def finish(np, audio, volume):
    """The audio at the volume asked, turned down to fit where it would clip; and a note about it, or None."""
    audio = audio * (volume / 100.0)
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    if peak < 1e-6:
        return audio, "the render is silent. Does the plugin need a kit or patch loaded (--edit), or is it an effect (--wav)?"
    if peak > 1.0:
        return audio / peak * 0.999, "turned down %.1f dB so it does not clip" % (20 * np.log10(peak / 0.999))
    return audio, None


# Stop asked: Pulsekit's Stop script (the PULSEKIT_STOP_FILE it makes) or Ctrl+C in a terminal. The step
# in progress (rendering, the test, the zips) ends after its file, and the run goes on to the next step
# with what was done; Ctrl+C again ends it at once.
STOP = {"asked": False}


def ctrl_c(sig, frame):
    if STOP["asked"]:
        raise KeyboardInterrupt
    STOP["asked"] = True
    print("\nStopping after the file in progress (Ctrl+C again ends it now)")


def stop_asked():
    """True once when a stop was asked (the ask is used up: the next step runs until asked again)."""
    f = os.environ.get("PULSEKIT_STOP_FILE")
    if f and os.path.exists(f):
        try:
            os.remove(f)
        except OSError:
            pass
        return True
    if STOP["asked"]:
        STOP["asked"] = False
        return True
    return False


def stoppable(items, step, after):
    """The items one by one, until a stop is asked: then a line saying how far the step got."""
    done = 0
    for item in items:
        if stop_asked():
            print("Stopped %s: %d of %d done; %s" % (step, done, len(items), after))
            return
        yield item
        done += 1


def ask_zip():
    """A --dir or --test run in a terminal: asks whether to zip what it used and made (no: Enter), and
    the largest zip in MB (Enter: one zip). None for no zip, else the MB limit (0: one zip)."""
    try:
        if not sys.stdin or not sys.stdin.isatty():
            return None
        if input("Put the MIDI files, the WAVs and the report into a zip? [y/N] ").strip().lower() not in ("y", "yes"):
            return None
        size = input("Largest zip in MB, for several zips (Enter: one zip) ").strip()
        try:
            return max(0.0, float(size)) if size else 0.0
        except ValueError:
            print("Not a number: one zip")
            return 0.0
    except (EOFError, OSError, ValueError):
        return None


ZIP_MB = 1000 * 1000


def packed_size(path):
    """How many bytes the file takes in a zip (deflated as zipfile does it)."""
    import zlib
    z = zlib.compressobj(zlib.Z_DEFAULT_COMPRESSION, zlib.DEFLATED, -15)
    n = 0
    with open(path, "rb") as f:
        while True:
            b = f.read(1 << 20)
            if not b:
                break
            n += len(z.compress(b))
    return min(n + len(z.flush()), os.path.getsize(path) + 64)


RESULTS_ZIP = "vst-test-results.zip"


def fill_codes(pattern, now, n):
    """The % codes filled in (as Pulsekit's Downloads subfolders): after a %, dd (day, 04), d (4), mm
    (month, 11), m, yyyy (2026), yy (26) and n (the number `n`) follow each other until another
    character; %% is a %, a lone % stays."""
    out = []
    i = 0
    while i < len(pattern):
        c = pattern[i]
        if c != "%":
            out.append(c)
            i += 1
            continue
        if pattern[i + 1:i + 2] == "%":
            out.append("%")
            i += 2
            continue
        j = i + 1
        while j < len(pattern):
            rest = pattern[j:]
            for code, value in (("yyyy", "%04d" % now.tm_year), ("yy", "%02d" % (now.tm_year % 100)), ("dd", "%02d" % now.tm_mday),
                                ("d", str(now.tm_mday)), ("mm", "%02d" % now.tm_mon), ("m", str(now.tm_mon)), ("n", str(n))):
                if rest.startswith(code):
                    out.append(value)
                    j += len(code)
                    break
            else:
                break
        if j == i + 1:
            out.append("%")
        i = max(j, i + 1)
    return "".join(out)


def expand_folder(pattern, now=None):
    """--zip_outdir's % codes filled in for today; %n is the first number from 1 whose folder (the path up
    to the part %n is in) is not there yet. No % codes: the path as it is."""
    import time
    if not pattern or "%" not in pattern:
        return pattern
    now = now or time.localtime()
    one, two = fill_codes(pattern, now, 1), fill_codes(pattern, now, 2)
    if one == two:
        return one
    at = next(i for i in range(min(len(one), len(two))) if one[i] != two[i])
    for n in range(1, 100000):
        path = fill_codes(pattern, now, n)
        # The folder %n numbers: up to the separator after where the number is.
        cut = len(path)
        for sep in ("/", "\\"):
            k = path.find(sep, at)
            if k >= 0:
                cut = min(cut, k)
        if not os.path.exists(path[:cut]):
            return path
    return one


ZIP_OUTDIR_EXPANDED = [False]


def copy_zips(zips, folder):
    """--zip_outdir: the run's zips copied into the folder (made when missing; a zip of the same name there
    is replaced). The copies' paths."""
    import shutil
    if not zips:
        print("Zip folder: no zip made in this run, nothing copied to %s" % folder)
        return []
    try:
        if not os.path.isdir(folder):
            os.makedirs(folder)
    except OSError as e:
        print("Zip folder: %s could not be made (%s): the zips stay where they are" % (folder, e))
        return []
    out = []
    for z in zips:
        target = os.path.join(folder, os.path.basename(z))
        if os.path.abspath(target) == os.path.abspath(z):
            out.append(z)
            continue
        try:
            shutil.copyfile(z, target)
            out.append(target)
        except OSError as e:
            print("Zip folder: %s not copied (%s)" % (os.path.basename(z), e))
    print("Zip folder: %d zip%s copied to %s" % (len(out), "" if len(out) == 1 else "s", folder))
    return out


def results_zip(dirs):
    """--test: every test's report, test beat (its MP3, else its WAV), times and kit profile in the folders
    (vst-test-*.txt, vst-check-*, vst-kit-*.kit; not in subfolders) into one zip, RESULTS_ZIP, in the first
    folder. The zip is added to, not made anew: what it holds stays, a file of the same name is replaced,
    so after runs of several plugins it holds them all. Then the reports, test beats and times files are
    deleted (they are in the zip); the kit profiles stay (--kit, --compare_kits). A test beat goes only
    with its times file (a --check WAV has none and stays). The test beats' hits (vst-hits-*/ with its
    hits.txt) go in as their folders, and stay on disk too: the drum sound library links to them. Its
    path, or None when there was nothing."""
    import zipfile
    files, loose = [], []
    for d in dirs:
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            p = os.path.join(d, f)
            if not os.path.isfile(p) or p in files:
                continue
            low = f.lower()
            report = low.startswith("vst-test-") and low.endswith(".txt")
            kit = low.startswith("vst-kit-") and low.endswith(".kit")
            times = low.startswith("vst-check-") and low.endswith("-times.txt")
            beat = low.startswith("vst-check-") and low.endswith((".mp3", ".wav")) and os.path.isfile(p[:-4] + "-times.txt")
            if beat and low.endswith(".wav") and os.path.isfile(p[:-4] + ".mp3"):
                loose.append(p)
                continue
            if report or kit or times or beat:
                files.append(p)
                if not kit:
                    loose.append(p)
    # The hits folders: each file under its folder's name (vst-hits-<plugin>/<file>).
    folders = []
    for d in dirs:
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            h = os.path.join(d, f)
            if f.lower().startswith("vst-hits-") and os.path.isfile(os.path.join(h, "hits.txt")) and h not in folders:
                folders.append(h)
    if not files and not folders:
        return None
    path = os.path.join(dirs[0], RESULTS_ZIP)
    work = path + ".part"
    names = {}
    for p in files:
        names.setdefault(os.path.basename(p), p)
    hits = 0
    for h in folders:
        for f in sorted(os.listdir(h)):
            if os.path.isfile(os.path.join(h, f)) and names.setdefault(os.path.basename(h) + "/" + f, os.path.join(h, f)) == os.path.join(h, f):
                hits += 1
    kept = 0
    with zipfile.ZipFile(work, "w", zipfile.ZIP_DEFLATED) as z:
        if os.path.isfile(path):
            try:
                with zipfile.ZipFile(path) as old:
                    for info in old.infolist():
                        if info.filename not in names:
                            z.writestr(info, old.read(info.filename))
                            kept += 1
            except (zipfile.BadZipFile, OSError) as e:
                print("Results zip: %s could not be read (%s): made anew" % (RESULTS_ZIP, e))
        for name, p in sorted(names.items()):
            z.write(p, name)
    os.replace(work, path)
    removed = 0
    for p in loose:
        try:
            os.remove(p)
            removed += 1
        except OSError:
            pass
    print("Results zip: %s (%d files: the tests' reports, test beats, times and kit profiles%s%s; %.1f MB); %d loose file%s deleted, the kit profiles%s kept"
          % (path, len(names) + kept, ", %d in %d hits folder%s" % (hits, len(folders), "" if len(folders) == 1 else "s") if folders else "",
             ", %d from earlier runs" % kept if kept else "", os.path.getsize(path) / float(ZIP_MB), removed, "" if removed == 1 else "s",
             " and hits folders" if folders else ""))
    return path


def make_zip(where, plugin, argv, folder, played, wavs, dms, report, opts, bpm, limit_mb=0, no_wav=False, relfn=None, check=None):
    """--zip: the inputs and what was made in one zip beside the report (or, with --zip_mb, in zips of at
    most that many MB); their paths.

    midi/ the MIDI files played (their subfolders under --dir kept), midi-<bpm>bpm/ the copies played at
    --bpm's tempo, wav/ the renders, dm/ DrumMidi's MIDI files (--test), settings/ the settings files
    given, and at the top the report and vst-render-run.txt (the switches used). Split, each zip keeps a
    MIDI with its WAV and DrumMidi's MIDI, and has the report, the settings and the run in it too.
    check/ (--test): the test beat as played, its times and the kit profile learned from it, in every zip."""
    import time
    import zipfile
    if not os.path.isdir(where):
        os.makedirs(where)
    base = "vst-render-%s-%s" % (re.sub(r"[^A-Za-z0-9._-]+", "_", plugin), time.strftime("%Y%m%d-%H%M%S"))
    used = set()

    def arc(sub, f, root=None):
        if root and os.path.abspath(f).startswith(os.path.abspath(root) + os.sep):
            rel = os.path.relpath(f, root)
        else:
            # A path already made relative (two folders: under each one's name) stays as it is.
            rel = f if not os.path.isabs(f) and os.sep in f else os.path.basename(f)
        a = (sub + "/" if sub else "") + rel.replace(os.sep, "/")
        head, ext = os.path.splitext(a)
        k = 2
        while a in used:
            a = "%s-%d%s" % (head, k, ext)
            k += 1
        used.add(a)
        return a
    out_root = opts["--outdir"] or folder
    # Each MIDI's files together (a unit), then what every zip has (the shared ones).
    dm_left = list(dms)
    units = []
    for k, (m, copy) in enumerate(played):
        unit = []
        if m and os.path.isfile(m):
            unit.append((m, arc("midi" if m.lower().endswith((".mid", ".midi")) else "input", relfn(m) if relfn else m, None if relfn else folder)))
        if copy and os.path.isfile(copy):
            unit.append((copy, arc(("midi-%gbpm" % bpm) if bpm else "midi-at-tempo", relfn(m) if relfn else m, None if relfn else folder)))
        o = wavs[k] if k < len(wavs) else None
        if o and os.path.isfile(o):
            if not (no_wav and o.lower().endswith(".wav")):
                unit.append((o, arc("mp3" if o.lower().endswith(".mp3") else "wav", o, out_root)))
            dm = os.path.splitext(o)[0] + "-dm.mid"
            if dm in dm_left:
                dm_left.remove(dm)
                unit.append((dm, arc("dm", dm, out_root)))
        if unit:
            units.append(unit)
    for d in dm_left:
        units.append([(d, arc("dm", d, out_root))])
    shared = []
    if opts["--dir"] and opts["--dir"].lower().endswith(".pls") and os.path.isfile(opts["--dir"]):
        shared.append((opts["--dir"], arc("", opts["--dir"])))
    for key in ("--state", "--effect_state", "--carla", "--midimap"):
        f = opts[key]
        if f and os.path.isfile(f):
            shared.append((f, arc("settings", f)))
    if report and os.path.isfile(report):
        shared.append((report, arc("", report)))
    for f in check or []:
        if f and os.path.isfile(f) and not (no_wav and f.lower().endswith(".wav")):
            shared.append((f, arc("check", f)))
    run = "vst_render.py " + " ".join('"%s"' % a if " " in a else a for a in argv) + "\n"
    run_name = arc("", "vst-render-run.txt")

    # The zips: units in order, a new zip when the next unit would not fit (a unit bigger than the
    # limit gets a zip of its own).
    def cost(files):
        return sum(packed_size(f) + 120 + 2 * len(a.encode("utf-8")) for f, a in files)
    groups = [[]]
    if limit_mb and limit_mb > 0 and len(units) > 50:
        print("Zipping: measuring %d MIDI files' files for %g MB zips" % (len(units), limit_mb))
    if limit_mb and limit_mb > 0:
        limit = int(limit_mb * ZIP_MB)
        room = limit - cost(shared) - len(run) - 200 - 22
        filled = 0
        for unit in units:
            c = cost(unit)
            if groups[-1] and filled + c > room:
                groups.append([])
                filled = 0
            if c > room:
                print("Note: %s alone is bigger than %g MB: its zip is bigger" % (os.path.basename(unit[0][0]), limit_mb))
            groups[-1].append(unit)
            filled += c
    else:
        groups = [units]
    paths = []
    count = 0
    total_units = sum(len(g) for g in groups)
    zipped = 0
    stopped = False
    for g, group in enumerate(groups):
        if stopped:
            break
        name = base if len(groups) == 1 else "%s-part%dof%d" % (base, g + 1, len(groups))
        path = os.path.join(where, name + ".zip")
        n = 2
        while os.path.exists(path):
            path = os.path.join(where, "%s-%d.zip" % (name, n))
            n += 1
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
            for unit in group:
                if stop_asked():
                    print("Stopped zipping: %d of %d MIDI files' files zipped" % (zipped, total_units))
                    stopped = True
                    break
                for f, a in unit:
                    z.write(f, a)
                    count += 1
                zipped += 1
                if zipped % 50 == 0:
                    print("Zipping: %d of %d" % (zipped, total_units))
            for f, a in shared:
                z.write(f, a)
            z.writestr(run_name, run)
        paths.append(path)
    count += len(shared)
    if len(paths) == 1:
        print("Zipped %d file%s" % (count, "" if count == 1 else "s"))
    else:
        print("Zipped %d file%s in %d zips of at most %g MB (each has the report, the settings and the run)" % (count, "" if count == 1 else "s", len(paths), limit_mb))
    return paths


def main(argv):
    try:
        import signal
        signal.signal(signal.SIGINT, ctrl_c)
    except (ImportError, ValueError):
        pass
    opts = {"--plugin": None, "--carla": None, "--clap": None, "--clap_id": None, "--drumgizmo": None, "--midimap": None, "--state": None, "--wav": None, "--effect": None, "--effect_state": None,
            "--bpm": None, "--rate": "44100", "--tail": "2", "--volume": "100", "--bits": "16", "--dir": None, "--outdir": None,
            "--drummidi": None, "--java": "java", "--zip_mb": None, "--zip_outdir": None, "--mp3": None, "--map": None,
            "--plugin2": None, "--plugin3": None, "--plugin4": None, "--dir2": None, "--map2": None, "--map3": None, "--map4": None, "--note_map": None, "--chokes": None,
            "--kit_name": None, "--kit_name2": None, "--kit_name3": None, "--kit_name4": None}
    switches = set()
    files = []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a in opts:
            if i + 1 >= len(argv):
                fail("%s needs a value" % a)
            opts[a] = argv[i + 1]
            i += 2
            continue
        if a in ("--edit", "--scan", "--recursive", "--test", "--no-toms", "--snare-leak", "--faded-opens", "--no-kit", "--compare_kits", "--no-map", "--zip", "--zip_nowav", "--skip_existing", "--keep_wav", "--bpm_from_name", "--check"):
            switches.add(a)
        elif a.startswith("--"):
            fail("unknown switch %s" % a)
        else:
            files.append(a)
        i += 1
    if "--scan" in switches:
        scan()
        return
    moved = lead_first(argv, opts)
    if moved:
        print(moved[1])
        return main(moved[0])
    # A map or kit name for a plugin not given (its row unticked in Params): left out, not a stop.
    for k, m in [(k, m) for k, m, _ in MORE_PLUGINS] + [(k, n) for k, _, n in MORE_PLUGINS]:
        if opts[m] and not opts[k]:
            print("%s left out: it is the %s for %s, which is not given" % (m, "map" if m.startswith("--map") else "kit name", k))
            opts[m] = None
            argv = drop_switch(argv, m)
    # --zip_outdir's % codes: filled in once for the whole run (all plugins' zips in one folder).
    if opts["--zip_outdir"] and not ZIP_OUTDIR_EXPANDED[0]:
        ZIP_OUTDIR_EXPANDED[0] = True
        filled = expand_folder(opts["--zip_outdir"])
        if filled != opts["--zip_outdir"]:
            print("Zip folder: %s" % filled)
            opts["--zip_outdir"] = filled
            argv = list(argv)
            argv[argv.index("--zip_outdir") + 1] = filled
    more = [opts[k] for k, _, _ in MORE_PLUGINS if opts[k]]
    if more:
        if not opts["--plugin"]:
            fail("--plugin2 to --plugin4 add plugins to --plugin: give --plugin too")
        if opts["--carla"] or opts["--drumgizmo"]:
            fail("--plugin2 to --plugin4 are VST plugins played after --plugin; not with --carla or --drumgizmo")
        if "--edit" in switches:
            fail("--edit opens one plugin's window: save each plugin's settings with its own run first")
        runs = plugin_runs(argv, opts)
        done = []
        for k, (plug, one) in enumerate(runs):
            print("== Plugin %d of %d: %s" % (k + 1, len(runs), os.path.basename(os.path.normpath(plug))))
            try:
                main(one)
                done.append((plug, "done"))
            except SystemExit as e:
                done.append((plug, "failed" if e.code else "done"))
            print("")
        print("Plugins: " + ", ".join("%s %s" % (os.path.basename(os.path.normpath(p)), r) for p, r in done))
        if all(r == "failed" for p, r in done):
            sys.exit(1)
        return
    midi = next((f for f in files if f.lower().endswith((".mid", ".midi"))), None)
    out = next((f for f in files if f.lower().endswith((".wav", ".wave"))), None)
    # A CLAP plugin given as --plugin: it plays in Carla's rack as --clap does.
    if opts["--plugin"] and opts["--plugin"].lower().rstrip("/\\").endswith(".clap"):
        if opts["--clap"]:
            fail("give the CLAP plugin once: --clap or --plugin")
        opts["--clap"], opts["--plugin"] = opts["--plugin"], None
    LV2_WANTED["id"] = opts["--clap_id"]
    # A SoundFont (.sf2, .sfz: x42 AVL Drumkits' kits too) plays with Pulsekit's Midi2Wav, no plugin needed.
    soundfont = bool(opts["--plugin"]) and is_soundfont(opts["--plugin"])
    if soundfont:
        if opts["--carla"] or opts["--clap"] or opts["--effect"] or opts["--wav"] or "--edit" in switches:
            fail("%s is a SoundFont: Midi2Wav plays it; no --carla, --clap, --effect, --wav or --edit with it" % os.path.basename(opts["--plugin"]))
        if lv2_bundle(opts["--plugin"]):
            print("%s: a kit of the LV2 plugin %s, played here as a SoundFont (Midi2Wav); the plugin itself plays it through Carla"
                  % (os.path.basename(opts["--plugin"]), os.path.basename(lv2_bundle(opts["--plugin"]))))
    # An LV2 plugin (its .lv2 folder, or the .dll in it): played in Carla's rack, as a 32-bit VST2 is.
    if opts["--plugin"] and lv2_bundle(opts["--plugin"]) and not soundfont:
        rack = carla_rack()
        if not rack:
            fail("%s is an LV2 plugin: it plays in Carla's rack. Carla's rack (CarlaVstShell.dll or CarlaRack) was not found in: %s "
                 "(Files & locations: Carla rack plugin)" % (os.path.basename(lv2_bundle(opts["--plugin"])), "; ".join(carla_searched()) or "no folders"))
        uri, kit = lv2_pick(lv2_bundle(opts["--plugin"]))
        others = [n for u, n in lv2_plugins(lv2_bundle(opts["--plugin"])) if u != uri]
        print("%s is an LV2 plugin: it plays in Carla's rack; its plugin: %s%s" % (os.path.basename(lv2_bundle(opts["--plugin"])), kit,
              (" (the others, by --clap_id: %s)" % ", ".join(others)) if others else ""))
        opts["--bridged"], opts["--plugin"] = lv2_bundle(opts["--plugin"]), rack
        # Carla finds an LV2 plugin by its URI in the LV2 folders: the bundle's own folder is one of them
        # for this run and for Edit settings' Carla (LV2_PATH), wherever it was installed.
        lv2_dir = os.path.dirname(opts["--bridged"])
        os.environ["LV2_PATH"] = os.pathsep.join([lv2_dir] + [d for d in os.environ.get("LV2_PATH", "").split(os.pathsep) if d and d != lv2_dir])
        print("LV2 folder for Carla: %s (LV2_PATH; if Carla still does not find it, add that folder in Carla's Settings, Plugin Paths, LV2)" % lv2_dir)
    # A .dll that is not a plugin for this Python: said plainly; a 32-bit VST2 plays in Carla's rack, bridged.
    # (Not Carla's rack an LV2 is played in.)
    if opts["--plugin"] and not opts.get("--bridged") and not soundfont:
        problem = dll_problem(opts["--plugin"])
        if problem == "32":
            rack = carla_rack()
            if not rack:
                fail("%s is a 32-bit VST2 plugin, and vst_render runs 64-bit: install Carla (free at kx.studio/carla, with its "
                     "32-bit bridge) and it plays through Carla's rack; or use a 64-bit version of the plugin. Carla's rack (CarlaVstShell.dll or CarlaRack) "
                     "was not found in: %s" % (os.path.basename(opts["--plugin"]), "; ".join(carla_searched()) or "no folders"))
            print("%s is a 32-bit VST2: it plays in Carla's rack, through Carla's 32-bit bridge" % os.path.basename(opts["--plugin"]))
            opts["--bridged"], opts["--plugin"] = opts["--plugin"], rack
        elif problem:
            fail(problem)
    if opts["--clap_id"] and not opts["--clap"] and not lv2_bundle(opts.get("--bridged")):
        fail("--clap_id picks a plugin of --clap's file (or an LV2 bundle's): give --clap too")
    if opts["--clap"]:
        if opts["--carla"] or opts["--drumgizmo"]:
            fail("--clap plays one CLAP plugin in Carla's rack: not with --carla or --drumgizmo")
        if not os.path.exists(opts["--clap"]):
            fail("no CLAP plugin at %s" % opts["--clap"])
        if not opts["--plugin"]:
            opts["--plugin"] = carla_rack()
            if not opts["--plugin"]:
                fail("--clap needs Carla's Carla-Rack VST (Carla 2.6 or newer, free at kx.studio/carla; or give the rack with --plugin)")
    if opts["--carla"]:
        if "--edit" in switches:
            fail("--carla plays a project set up in Carla: change the kit there and save the project again")
        if not opts["--plugin"]:
            opts["--plugin"] = carla_rack()
            if not opts["--plugin"]:
                fail("--carla needs Carla's Carla-Rack VST (install Carla, or give it with --plugin)")
    gizmo = opts["--drumgizmo"]
    if gizmo:
        if opts["--plugin"] or opts["--carla"] or opts["--effect"] or opts["--wav"] or "--edit" in switches:
            fail("--drumgizmo plays the kit with DrumGizmo's own program: no --plugin, --carla, --effect, --wav or --edit with it")
        opts["--plugin"] = gizmo
    plugin = opts["--plugin"]
    if not plugin:
        fail("no plugin: give one with --plugin (--scan lists the installed ones)")
    # The WAVs and the report are named after the Carla project, the CLAP plugin or the kit, not the rack.
    name = opts["--carla"] or opts["--clap"] or opts.get("--bridged") or plugin
    if gizmo and stem(gizmo).lower() == "drumkit":
        name = os.path.basename(os.path.dirname(os.path.abspath(gizmo))) or gizmo
    folder = opts["--dir"]
    # One of SogniMusic's zips as --dir: it (and its other parts) unpacked beside it, and its MIDI files rendered.
    one_zip = None
    if folder and folder.lower().endswith(".zip") and os.path.isfile(folder):
        if not sogni_zip(folder):
            fail("%s is not a SogniMusic zip (--zip makes one)" % folder)
        one_zip = zip_base(folder)
        folder = os.path.dirname(os.path.abspath(folder))
    # A playlist (.pls) for the folder: its MIDI files (PlayList --midi makes one).
    pls = bool(folder) and folder.lower().endswith(".pls") and os.path.isfile(folder)
    if folder:
        if not os.path.isdir(folder) and not pls:
            fail("no folder %s" % folder)
        if opts["--wav"]:
            fail("--dir plays MIDI files; --wav is for one WAV")
        batch = pls_midis(folder) if pls else ([] if one_zip else collect(folder, "--recursive" in switches))
        # SogniMusic's zips: their MIDI files (an <zip>-midi folder made earlier is replaced, not counted twice).
        if not pls:
            for into, midis in sogni_zips(folder, one_zip, "--recursive" in switches and not one_zip).items():
                batch = [m for m in batch if not m.startswith(into + os.sep)] + midis
        if pls:
            folder = os.path.dirname(os.path.abspath(folder))
        roots = {m: folder for m in batch}
        if opts["--dir2"]:
            second = opts["--dir2"]
            if not os.path.isdir(second):
                fail("no folder %s (--dir2)" % second)
            more = collect(second, "--recursive" in switches)
            if not more:
                fail("no MIDI files in %s (--dir2)" % second)
            for m in more:
                roots.setdefault(m, second)
            batch = batch + [m for m in more if roots[m] == second]
        if not batch:
            fail("no MIDI files in %s" % folder)
    elif not midi and not opts["--wav"] and "--check" not in switches:
        fail("no MIDI file to play (or a WAV with --wav, or a folder with --dir)")
    else:
        batch = None
        roots = {}
    if opts["--dir2"] and not opts["--dir"]:
        fail("--dir2 is a second folder to --dir: give --dir too")
    # With two folders, each one's files go under its own name in --outdir.
    two = bool(opts["--dir2"])
    names = {}
    for r in sorted(set(roots.values()), key=lambda r: r != folder):
        n = os.path.basename(os.path.normpath(r)) or "folder"
        names[r] = n if n not in names.values() else "%s-%d" % (n, len(names) + 1)

    def rel(m):
        """The MIDI's path under its folder (with two folders, under the folder's name)."""
        r = roots.get(m, folder)
        sub = os.path.relpath(m, r) if r else os.path.basename(m)
        return os.path.join(names[r], sub) if two and r in names else sub

    def shown(path):
        """A path as the log shows it: under --outdir, else under its folder."""
        if opts["--outdir"]:
            return os.path.relpath(path, opts["--outdir"])
        for r in roots.values():
            if os.path.abspath(path).startswith(os.path.abspath(r) + os.sep):
                return (names[r] + os.sep if two else "") + os.path.relpath(path, r)
        return path
    test = "--test" in switches
    # The plugin's name in the output files, with the kit loaded in it (--kit_name). Left blank, a
    # plugin's kit is named for what it plays: the kit saved for it (--edit, --state), else its default.
    if not (opts["--kit_name"] or "").strip() and (opts["--plugin"] or opts["--clap"]) and not opts["--carla"] and not opts["--drumgizmo"]:
        # A CLAP or bridged plugin's settings are kept under its own name (not the rack's).
        plug = opts["--clap"] or opts.get("--bridged") or opts["--plugin"]
        saved = os.path.isfile(state_home(plug)) or (opts.get("--bridged") and os.path.isfile(bridged_project(plug)))
        opts["--kit_name"] = "saved kit" if opts["--state"] or saved else "default kit"
        print("Kit: %s (no --kit_name given)" % opts["--kit_name"])
    elif opts["--kit_name"]:
        print("Kit: %s (--kit_name)" % opts["--kit_name"])
    tag = kit_tag(name, opts["--kit_name"])
    if test:
        if opts["--wav"]:
            fail("--test checks a MIDI's render; --wav has no MIDI to check against")
        jar = opts["--drummidi"]
        if not jar or not os.path.isfile(jar):
            fail("--test needs DrumMidi_CRT.jar (--drummidi; Pulsekit gives its own)")
    nmap = {}
    map_part = None
    if test and not (opts["--map"] or "").strip() and "--no-map" not in switches and (opts["--plugin"] or opts["--clap"]):
        # No --map given: the map Pulsekit keeps (drum-kits.txt) when it has a section for this plugin
        # (Anarchy, Riot, Ugritone/KVLT, Northern Artillery): what the plugin really plays for the MIDI's notes.
        known, part = default_map(name)
        if known:
            opts["--map"] = known
            print("Map: drum-kits.txt, section [%s] (no --map given; --no-map leaves it out)" % part)
    if opts["--map"] and not test and "--check" not in switches:
        # The map says what the plugin plays for a --test's notes: a render without a test has no use for it.
        print("Map: left out (--map is for --test and --check: what the plugin plays for the MIDI's notes)")
        opts["--map"] = None
    if opts["--map"]:
        try:
            nmap, map_part = map_for(map_file(opts["--map"]), name)
        except (ValueError, OSError) as e:
            fail("--map: %s" % e)
    mp3_kbps = None
    if opts["--mp3"]:
        try:
            mp3_kbps = int(opts["--mp3"])
        except ValueError:
            fail("--mp3 takes kbps (128, 192, 320...)")
        if mp3_kbps not in MP3_RATES:
            fail("--mp3 is one of %s kbps" % ", ".join(str(r) for r in MP3_RATES))
        if not opts["--drummidi"] or not os.path.isfile(opts["--drummidi"]):
            fail("--mp3 needs Pulsekit's DrumMidi_CRT.jar, which has Wav2Mp3 (--drummidi; Pulsekit gives its own)")
    try:
        rate = int(opts["--rate"])
        tail = float(opts["--tail"])
        volume = float(opts["--volume"])
        bits = int(opts["--bits"])
    except ValueError:
        fail("--rate, --tail, --volume and --bits take numbers")
    if rate not in (44100, 48000, 88200, 96000):
        fail("--rate is 44100 or 48000 (or 88200, 96000)")
    if not 1 <= volume <= 400:
        fail("--volume is 1 to 400")
    if bits not in (16, 24):
        fail("--bits is 16 or 24")
    tail = max(0.0, min(30.0, tail))
    zip_mb = 0.0
    if opts["--zip_mb"]:
        try:
            zip_mb = float(opts["--zip_mb"])
        except ValueError:
            fail("--zip_mb takes a number (MB)")
        if zip_mb < 1:
            fail("--zip_mb is 1 or more (MB)")
    bpm = None
    if opts["--bpm"]:
        try:
            bpm = float(opts["--bpm"])
        except ValueError:
            fail("--bpm takes a number (beats a minute)")
        if not 20 <= bpm <= 400:
            fail("--bpm is 20 to 400")
        if opts["--wav"]:
            fail("--bpm is a MIDI's tempo; --wav plays a WAV as it is")
    by_name = "--bpm_from_name" in switches
    if by_name and opts["--wav"]:
        fail("--bpm_from_name is a MIDI's tempo; --wav plays a WAV as it is")
    # --bpm (and --bpm_from_name): each MIDI is played from a copy at that tempo (the file itself stays as it is).
    import tempfile
    tempo_dir = tempfile.mkdtemp(prefix="vst-bpm-") if bpm or by_name else None
    played_as = {}
    tempo_of = {}

    def tempo(m):
        """The tempo the MIDI is played at: --bpm; with --bpm_from_name its own if it has one, else its
        name's, else --bpm; None: its own."""
        if m not in tempo_of:
            if not by_name:
                tempo_of[m] = bpm
            else:
                own = file_tempo(m)
                tempo_of[m] = None if own else (name_tempo(m, roots.get(m)) or bpm)
        return tempo_of[m]

    def at_tempo(m):
        b = tempo(m)
        if not b:
            return m
        if m not in played_as:
            played_as[m] = retempo(m, b, tempfile.mkdtemp(dir=tempo_dir))
        return played_as[m]

    def tempo_note(m):
        b = tempo(m)
        if not b:
            if not by_name:
                return ""
            own, named = file_tempo(m), name_tempo(m, roots.get(m))
            if own and named and abs(own - named) > 1:
                return " at its own %g BPM (its name says %d)" % (own, named)
            return " at its own %g BPM" % own if own else ""
        return " at %g BPM (%s)" % (b, "name" if by_name and name_tempo(m, roots.get(m)) else "--bpm")
    if by_name:
        print("Tempo: each MIDI's own; one with none takes its name's or its folder's%s" % (", else %g BPM (--bpm)" % bpm if bpm else ""))
    if bpm:
        print("Tempo: %g BPM (--bpm)" % bpm)
    try:
        import numpy as np
    except ImportError as e:
        fail("numpy is not installed (%s). Install it with: pip install numpy" % e, 2)
    if gizmo:
        chain = Gizmo(rate, opts)
    elif soundfont:
        chain = SoundFont(rate, opts)
    else:
        try:
            import dawdreamer as daw
        except ImportError as e:
            fail("DawDreamer is not installed (%s). Install it with: pip install dawdreamer" % e, 2)
        chain = Chain(daw, rate, opts, switches)
    # The notes sent to the plugin (--note_map, else map.txt's lines for it).
    try:
        chain.note_map, said = pad_map(opts["--note_map"], name)
    except (ValueError, OSError) as e:
        fail("--note_map: %s" % e)
    if said:
        print(said)
    late = chain.latency()
    if late:
        print("Latency: %d samples (%.1f ms), taken off the start" % (late, 1000.0 * late / rate))
    kit_file = None
    choke_lines = []
    kit_said = "not learned (--no-kit, or no test beat)"
    # --test: the test beat as played and its times, kept beside the report (and zipped in check/).
    check_files = []
    if not opts["--wav"]:
        # The plugin's check: a test beat through it. A plugin silent for it plays nothing for the run either.
        beat = check_midi(tempfile.mkdtemp(prefix="vst-check-"))
        # Every drum and cymbal played once first, unheard: a sampler that loads a sound when it is
        # first played (KVLT) has them all for the test beat.
        if not gizmo and not soundfont and warm_up(chain, np):
            print("Warm-up: every drum and cymbal played once before the test beat (a plugin that loads a sound when it is first played has them now)")
        try:
            check_audio, _, _ = chain.render(np, midi=beat, tail=1.0)
        except ValueError as e:
            fail("the test beat did not play (%s)" % e)
        check_audio, check_note = finish(np, check_audio, volume)
        if check_note and check_note.startswith("the render is silent"):
            # A sampler still loading its kit (KVLT takes long to load a preset) is silent at first:
            # the test beat again every 5 s, for up to the kit's load time (Edit settings' timer; else
            # 30 s), before it counts as silent.
            import time
            limit = getattr(chain, "load_limit", LOAD_WAIT)
            waited = 0
            while check_note and check_note.startswith("the render is silent") and waited < limit and not STOP["asked"]:
                if waited == 0:
                    print("Plugin check: silent at first (the kit still loading?): trying again every 5 s, for up to %.0f s" % limit)
                step = min(5.0, limit - waited)
                time.sleep(step)
                waited += step
                try:
                    check_audio, _, _ = chain.render(np, midi=beat, tail=1.0)
                except ValueError:
                    break
                check_audio, check_note = finish(np, check_audio, volume)
            if waited and not (check_note and check_note.startswith("the render is silent")):
                print("Plugin check: it plays after %.0f s" % waited)
        if check_note and check_note.startswith("the render is silent") and not gizmo:
            # Silent for the GM drum notes: maybe its pads are on other notes (a sampler's C4 up). They are named.
            plays = note_sweep(chain, np, rate)
            if plays and not chain.note_map:
                print_suggestion(chain, np, rate, name, plays)
                fail("%s plays nothing for the GM drum notes of a test beat (kick, snare, hats: 36, 38, 42), but it plays notes %s: "
                     "its pads are on those notes. Set its pads to the GM notes in Edit settings, or send the notes there with a note map "
                     "(Files & locations: map.txt, e.g. 36=%d)"
                     % (os.path.basename(os.path.normpath(name)), named_list(plays), plays[0]))
            print("Notes: none of %d to %d sounds either (each played alone)" % (SWEEP_LOW, SWEEP_HIGH))
        if check_note and check_note.startswith("the render is silent"):
            fail("%s plays nothing for a test beat (kick, snare, hats: GM notes 36, 38, 42), so it is skipped: %s"
                 % (os.path.basename(os.path.normpath(name)), silent_note(check_note, gizmo, opts["--carla"] or opts.get("--bridged"))))
        peak = float(np.max(np.abs(check_audio)))
        # Where the sound is in the test beat (its first notes are at 0 s): a plugin heard only late, or only
        # in a short burst, plays the beat wrong (a bridge starting, a kit loading).
        loud = np.nonzero(np.max(np.abs(check_audio), axis=0) > peak * 0.05)[0]
        span = ""
        if loud.size:
            secs = check_audio.shape[1] / float(rate)
            blocks = np.unique(loud // int(rate * 0.5))
            span = "; sound from %.1f s to %.1f s of %.1f s, in %d of its %d half-seconds" % (
                loud[0] / float(rate), loud[-1] / float(rate), secs, blocks.size, int(np.ceil(secs * 2)))
        print("Plugin check: the test beat plays (peak %.1f dBFS%s)" % (20 * np.log10(max(peak, 1e-9)), span))
        # Another kit's test beat, sound for sound: the kit was named, not changed.
        same = same_kit_check(np, check_audio, rate, opts.get("--bridged") or opts["--clap"] or name, opts["--kit_name"])
        if same:
            print(same)
        # Which of its notes sound (each drum and cymbal played alone): a plugin with pads on other notes
        # plays only some of them.
        heard, silent = notes_heard(np, check_audio, rate, [(t, n) for t, n, _, tag in kit_events() if tag == ""])
        # Notes the note map leaves out (=none) are silent on purpose.
        off = [n for n in silent if chain.note_map and n in chain.note_map and chain.note_map[n] is None]
        silent = [n for n in silent if n not in off]
        if off:
            print("Notes left out by the note map: %s" % named_list(off))
        if silent:
            print("Notes: %s heard; %s silent (each played alone)%s" % (named_list(heard) or "none", named_list(silent),
                  ": its pads may be on other notes (set them in Edit settings, or a note map: Files & locations, map.txt)" if len(silent) > len(heard) else ""))
            # Most drums silent: which notes its pads are on, what each sounds like, and a note map for them.
            # (With a note map in use, it is not suggested again: map.txt is edited instead.) Some drums
            # silent (no crash on 49, no ride on 51): those looked for on the plugin's other notes.
            if not gizmo and not chain.note_map:
                if len(silent) > len(heard):
                    print_suggestion(chain, np, rate, name)
                elif (set(KEY_NOTES) | set(GM_TOMS)) & set(silent):
                    # (Toms too: a kit with two toms leaves 45-50 silent; the songs' toms then go to the two.)
                    print("Notes: %s silent: looking for them on the plugin's other notes" % named_list(sorted((set(KEY_NOTES) | set(GM_TOMS)) & set(silent))))
                    if not print_suggestion(chain, np, rate, name, only=set(silent)):
                        print("Notes: no pad on other notes sounds like them: the kit has none (or Edit settings can load one)")
        else:
            print("Notes: all heard (%s)" % named_list(heard))
        # The kit's own kick and toms (the beat's second part), for DrumMidi in the test.
        if "--no-kit" not in switches and opts["--drummidi"] and os.path.isfile(opts["--drummidi"]) and (test or "--check" in switches):
            where = opts["--outdir"] or "."
            if not os.path.isdir(where):
                os.makedirs(where)
            kit_file = os.path.join(where, "vst-kit-%s.kit" % tag)
            said = learn_kit(opts["--java"], opts["--drummidi"], check_audio, rate, bits, nmap, kit_file)
            if kit_missing(said):
                # A sampler still loading its kit (KVLT takes long to load a preset) plays the first
                # renders without some drums: the test beat again every 5 s, for up to 30 s, until every
                # tom the map plays is heard; the kit is learned from the render with the most of them.
                import time
                import shutil
                waited = 0
                first = kit_missing(said)
                best = first
                trial = kit_file + ".try"
                limit = getattr(chain, "load_limit", LOAD_WAIT)
                while best and waited < limit and not STOP["asked"]:
                    step = min(5.0, limit - waited)
                    time.sleep(step)
                    waited += step
                    try:
                        again, _, _ = chain.render(np, midi=beat, tail=1.0)
                    except ValueError:
                        break
                    again, _ = finish(np, again, volume)
                    said2 = learn_kit(opts["--java"], opts["--drummidi"], again, rate, bits, nmap, trial)
                    if said2.startswith("Wrote ") and kit_missing(said2) < best:
                        best = kit_missing(said2)
                        shutil.move(trial, kit_file)
                        said = said2.replace(os.path.basename(trial), os.path.basename(kit_file))
                        check_audio = again
                if os.path.isfile(trial):
                    os.remove(trial)
                print("Kit: %d of the kick and toms silent in the first test beat (the kit still loading?); %s after %.0f s (waited at most %.0f s%s)"
                      % (first, "all heard" if not best else "%d still silent" % best, waited, limit,
                         ", the kit's load time" if limit != LOAD_WAIT else "; Edit settings' Stop timer sets it"))
            if said.startswith("Wrote ") and "no tom played" in said:
                # A kit without toms (DigiDrum's five pads): DrumMidi takes no profile without a tom, so none is used.
                kit_file = None
                kit_said = "not used (%s): no tom played, and DrumMidi needs a kick and a tom; the test went on without it" % said[6:]
                print("Kit: not used (no tom played: a kit profile needs a kick and a tom); the test goes on without it")
            elif said.startswith("Wrote "):
                kit_said = said[6:]
                print("Kit: " + said[6:])
            else:
                kit_file = None
                kit_said = "not learned (%s); the test went on without it" % said
                print("Kit: not learned (%s); the test goes on without it" % said)
        # The plugin's chokes (--chokes): played alone after the test beat, and what DrumMidi writes there.
        if opts["--chokes"] and opts["--drummidi"] and os.path.isfile(opts["--drummidi"]) and not gizmo:
            try:
                pairs = map_for(opts["--chokes"])[0]
            except ValueError as e:
                fail("--chokes: %s" % e)
            choke_lines = choke_beat(chain, np, rate, bits, opts, kit_file, pairs)
            for line in choke_lines:
                print(line)
        if test and "--check" not in switches:
            where = opts["--outdir"] or "."
            if not os.path.isdir(where):
                os.makedirs(where)
            keep = os.path.join(where, "vst-check-%s.wav" % tag)
            write_wav(keep, check_audio, rate, bits)
            times_keep = os.path.join(where, "vst-check-%s-times.txt" % tag)
            with open(times_keep, "w") as f:
                f.write(kit_times_text(nmap))
            check_files = [keep, times_keep]
            print("Test beat: %s (%.1f s) and its times, %s" % (os.path.basename(keep), check_audio.shape[1] / float(rate), os.path.basename(times_keep)))
            # Its hits cut into a folder of their own, for Pulsekit's drum sound library (DrumMidi --cut-hits).
            if opts["--drummidi"] and os.path.isfile(opts["--drummidi"]):
                hits = os.path.abspath(os.path.join(where, "vst-hits-%s" % tag))
                said = cut_hits(opts["--java"], opts["--drummidi"], keep, times_keep, hits)
                if said.startswith("Cut "):
                    print("Hits for the drum sound library: %s (%s)" % (hits, said))
                else:
                    print("Hits not cut for the drum sound library: %s" % said)
        if "--check" in switches:
            where = opts["--outdir"] or "."
            if not os.path.isdir(where):
                os.makedirs(where)
            out = os.path.join(where, "vst-check-%s.wav" % tag)
            write_wav(out, check_audio, rate, bits)
            print("Wrote %s: %.1f s, the test beat" % (out, check_audio.shape[1] / float(rate)))
            print("Succeeded: %s plays" % os.path.basename(os.path.normpath(name)))
            return
    elif "--check" in switches:
        fail("--check plays a test beat (MIDI); --wav plays a WAV through an effect")
    if batch is None:
        source = opts["--wav"] or midi
        if not os.path.isfile(source):
            fail("no file at %s" % source)
        if midi and not opts["--wav"]:
            length, notes = midi_notes(at_tempo(midi))
            if not notes:
                fail("%s has no notes" % os.path.basename(midi))
            print("MIDI: %s, %d notes, %.1f s" % (os.path.basename(midi), len(notes), length))
        try:
            audio, length, late = chain.render(np, midi=None if opts["--wav"] else at_tempo(midi), wav=opts["--wav"], tail=tail)
        except ValueError as e:
            fail(str(e))
        if opts["--wav"]:
            print("Input: %s, %.1f s" % (os.path.basename(source), length))
        audio, note = finish(np, audio, volume)
        note = silent_note(note, gizmo, opts["--carla"] or opts.get("--bridged"))
        if note:
            print("Note: " + note)
        if not out:
            out = "%s-%s.wav" % (stem(source), tag)
        if opts["--outdir"]:
            if not os.path.isdir(opts["--outdir"]):
                os.makedirs(opts["--outdir"])
            out = os.path.join(opts["--outdir"], os.path.basename(out))
        write_wav(out, audio, rate, bits)
        print("Wrote %s: %.1f s, %d Hz, %d-bit stereo" % (out, audio.shape[1] / float(rate), rate, bits))
        batch_done = [(midi, out)]
    else:
        print("vst_render: %d MIDI file%s in %s%s%s" % (len(batch), "" if len(batch) == 1 else "s", folder, " and " + opts["--dir2"] if two else "", " (and their subfolders)" if two and "--recursive" in switches else " (and its subfolders)" if "--recursive" in switches else ""))
        batch_done = []
        skip = "--skip_existing" in switches
        kept = 0
        kept_log = KeptLog("renders", len(batch))
        again_log = KeptLog("renders", len(batch), "Again", "silent before: made again")
        silent = 0
        rendered = 0
        for m in stoppable(batch, "rendering", "the test and the zip use the WAVs made"):
            where = opts["--outdir"] or os.path.dirname(m)
            if opts["--outdir"] and (not pls or roots.get(m) != folder):
                # Subfolders are kept under --outdir (with two folders, under each one's name).
                where = os.path.join(opts["--outdir"], os.path.dirname(rel(m)))
            o = os.path.join(where, "%s-%s.wav" % (stem(m), tag))
            # --skip_existing: a WAV a stopped run made is kept, when it is whole.
            if skip and whole_wav(o) and silent_render(o, rate):
                again_log.kept(shown(o), "the WAV made before is silent")
            elif skip and whole_wav(o):
                kept_log.kept(shown(o))
                batch_done.append((m, o))
                kept += 1
                continue
            # An MP3 of it from a --mp3 run (its WAV deleted): the MIDI's render too.
            made_mp3 = os.path.splitext(o)[0] + ".mp3"
            if skip and os.path.isfile(made_mp3) and os.path.getsize(made_mp3) > 0 and silent_render(made_mp3, rate):
                again_log.kept(shown(made_mp3), "DrumMidi found no hits in the MP3 made before: silent")
            elif skip and os.path.isfile(made_mp3) and os.path.getsize(made_mp3) > 0:
                kept_log.kept(shown(made_mp3))
                batch_done.append((m, made_mp3))
                kept += 1
                continue
            try:
                audio, length, _ = chain.render(np, midi=at_tempo(m), tail=tail)
            except Exception as e:
                print("Failed %s: %s" % (rel(m), e))
                continue
            audio, note = finish(np, audio, volume)
            if note and note.startswith("the render is silent"):
                # Nothing to test or keep: the MIDI's notes are ones this kit does not play.
                silent += 1
                print("Skipped %s: %s" % (rel(m), silent_note(note, gizmo, opts["--carla"] or opts.get("--bridged"))))
                if silent >= SILENT_STOP and rendered == 0:
                    print("Stopped: the first %d renders are silent. Load a kit or patch the MIDI's notes play (--edit), "
                          "check the plugin (--check), or give a --map" % silent)
                    break
                continue
            rendered += 1
            if not os.path.isdir(where):
                os.makedirs(where)
            write_wav(o, audio, rate, bits)
            print("Wrote %s: %.1f s%s%s" % (shown(o), audio.shape[1] / float(rate), tempo_note(m), " (" + note + ")" if note else ""))
            batch_done.append((m, o))
        kept_log.end()
        again_log.end()
    dms = []
    rep = None
    # The plugin's (or project's, kit's) name: the test loop's `name` is each WAV's.
    plugin_name = name
    if test:
        report = ["vst_render --test: DrumMidi_CRT on each render, its hits against the MIDI played",
                  "Plugin: %s%s%s" % (os.path.basename(os.path.normpath(name)), ", kit " + opts["--kit_name"] if opts["--kit_name"] else "",
                                      ", effect " + os.path.basename(os.path.normpath(opts["--effect"])) if opts["--effect"] else ""),
                  "Notes mapped (--map%s): " % ("" if map_part is None else ", section [%s]" % map_part if map_part else ", no section for this plugin") + (", ".join("%d=%s" % (a, "none" if b is None else b) for a, b in sorted(nmap.items())) if nmap else "none"),
                  "Note map (the notes sent to the plugin, --note_map or map.txt): " + (", ".join("%d=%s" % (a, "none" if b is None else b) for a, b in sorted(chain.note_map.items())) if chain.note_map else "none"),
                  "Kit profile (the test beat's kick and toms): " + kit_said,
                  ] + list(choke_lines) + [
                  "P: DrumMidi's hits that were played, R: played hits DrumMidi found (within %d ms, after the best shift)" % (TOLERANCE * 1000), ""]
        total = {}
        cymbals = [[0] * (len(CYMBAL_KINDS) + 1) for _ in CYMBAL_KINDS]
        cymbal_extra = [0] * len(CYMBAL_KINDS)
        tested = 0
        kept_dm = KeptLog("DrumMidi MIDI files", len(batch_done))
        # --compare_kits: DrumMidi again on each render with the other profiles (their MIDI files in a temporary folder).
        compare = []
        if "--compare_kits" in switches and opts["--drummidi"]:
            import tempfile
            compare = compare_profiles(os.path.dirname(kit_file) if kit_file else (opts["--outdir"] or "."), kit_file)
            compare_dir = tempfile.mkdtemp(prefix="vst-compare-")
            print("Comparing kit profiles: " + ", ".join(label for label, _ in compare))
        compared = [(label, {}, [[0] * (len(CYMBAL_KINDS) + 1) for _ in CYMBAL_KINDS]) for label, _ in compare]
        print("Testing %d render%s with DrumMidi_CRT" % (len(batch_done), "" if len(batch_done) == 1 else "s"))
        for m, o in stoppable(batch_done, "the test", "the report has the WAVs tested"):
            tested += 1
            dm = os.path.splitext(o)[0] + "-dm.mid"
            no_toms = "--no-toms" in switches
            said = []
            # DrumMidi's MIDI of this render from a stopped run: used as it is. (A kept MP3 is newer than
            # the MIDI made from its WAV before the WAV became the MP3.)
            if "--skip_existing" in switches and os.path.isfile(dm) and os.path.getsize(dm) > 0 \
                    and (os.path.getmtime(dm) >= os.path.getmtime(o) or o.lower().endswith(".mp3")):
                found = midi_notes(dm)[1]
                kept_dm.kept(os.path.basename(dm))
                said = None
            else:
                found = drum_midi(opts["--java"], opts["--drummidi"], o, dm, (["--no-toms"] if no_toms else []) + (["--snare-leak"] if "--snare-leak" in switches else []) + (["--faded-opens"] if "--faded-opens" in switches else [])
                                  + (["--kit", kit_file] if kit_file else []), said)
            if os.path.isfile(dm):
                dms.append(dm)
            name = os.path.basename(o)
            if isinstance(found, str):
                report.append("%s: %s" % (name, found))
                print("Tested %d of %d: %s: %s" % (tested, len(batch_done), name, found))
                continue
            length, played = midi_notes(at_tempo(m))
            played = mapped(played, nmap)
            if no_toms:
                played = [(t, n) for t, n in played if n not in TOMS]
            rows, shift = score(played, found, length + 0.05)
            if no_toms:
                rows = [r for r in rows if r[0] != "Toms"]
            for fam, t, f, ok in rows:
                v = total.setdefault(fam, [0, 0, 0])
                v[0] += t
                v[1] += f
                v[2] += ok
            table, extra = cymbal_breakdown(played, found, length + 0.05, shift)
            for a in range(len(table)):
                for b in range(len(table[a])):
                    cymbals[a][b] += table[a][b]
                cymbal_extra[a] += extra[a]
            swaps = swaps_line(table)
            report.append("%s (shift %+d ms): %s%s" % (name, round(shift * 1000), score_line(rows), "\n    swapped: " + swaps if swaps else ""))
            base = (["--no-toms"] if no_toms else []) + (["--snare-leak"] if "--snare-leak" in switches else []) + (["--faded-opens"] if "--faded-opens" in switches else [])
            for k, ((label, prof), (_, ctot, ccym)) in enumerate(zip(compare, compared)):
                if STOP["asked"]:
                    break
                other = drum_midi(opts["--java"], opts["--drummidi"], o, os.path.join(compare_dir, "%d-%s.mid" % (k, os.path.splitext(name)[0])),
                                  base + (["--kit", prof] if prof else []))
                if isinstance(other, str):
                    continue
                crows, cshift = score(played, other, length + 0.05)
                for fam, t, f, ok in crows:
                    if no_toms and fam == "Toms":
                        continue
                    v = ctot.setdefault(fam, [0, 0, 0])
                    v[0] += t
                    v[1] += f
                    v[2] += ok
                ctable, _ = cymbal_breakdown(played, other, length + 0.05, cshift)
                for a in range(len(ctable)):
                    for b in range(len(ctable[a])):
                        ccym[a][b] += ctable[a][b]
            if compare:
                print("Compared %d of %d: %s with %d other kit profile%s" % (tested, len(batch_done), name, len(compare), "" if len(compare) == 1 else "s"))
            if said is not None:
                # Each file's line as it is done (Output would else stay silent through the whole test,
                # and PyJav takes a run silent for 600 s as hung), with what DrumMidi wrote.
                wrote = [l for l in said if l.startswith("Wrote ")]
                print("Tested %d of %d: %s (shift %+d ms): %s%s" % (tested, len(batch_done), name, round(shift * 1000), score_line(rows),
                                                                  " | DrumMidi: " + wrote[-1][6:] if wrote else ""))
        kept_dm.end()
        if tested < len(batch_done):
            report.append("Stopped: %d of %d WAVs tested" % (tested, len(batch_done)))
        if total:
            report.append("")
            report.append("All: " + score_line([(fam, total[fam][0], total[fam][1], total[fam][2]) for fam, notes in FAMILIES if fam in total]))
            if any(sum(r) for r in cymbals) or any(cymbal_extra):
                report.append("")
                report.extend(breakdown_text(cymbals, cymbal_extra))
            if compare:
                own = ("this kit's own (%s)" % os.path.basename(kit_file) if kit_file else "no profile (the test's)", total, cymbals)
                report.append("")
                report.extend(compare_text(own, compared))
        text = "\n".join(report)
        print("")
        print(text)
        where = opts["--outdir"] or (folder if batch is not None else os.path.dirname(os.path.abspath(batch_done[0][1])))
        rep = os.path.join(where, "vst-test-%s.txt" % kit_tag(plugin_name, opts["--kit_name"]))
        with open(rep, "w") as f:
            f.write(text + "\n")
        print("Wrote %s" % rep)
    if mp3_kbps and batch_done:
        wav_mb = mp3_mb = 0.0
        converted = 0
        for k in stoppable(list(range(len(batch_done))), "MP3", "the rest stay WAVs"):
            m, o = batch_done[k]
            if o.lower().endswith(".mp3"):
                continue
            mp3 = os.path.splitext(o)[0] + ".mp3"
            why = to_mp3(opts["--java"], opts["--drummidi"], o, mp3, mp3_kbps)
            if why:
                print("MP3 failed %s: %s (the WAV stays)" % (os.path.basename(o), why))
                continue
            wav_mb += os.path.getsize(o) / float(ZIP_MB)
            mp3_mb += os.path.getsize(mp3) / float(ZIP_MB)
            if "--keep_wav" not in switches:
                os.remove(o)
            batch_done[k] = (m, mp3)
            converted += 1
            print("MP3 %s: %.2f MB" % (os.path.basename(mp3), os.path.getsize(mp3) / float(ZIP_MB)))
        for k, f in enumerate(check_files):
            if f.lower().endswith(".wav") and os.path.isfile(f):
                mp3 = os.path.splitext(f)[0] + ".mp3"
                why = to_mp3(opts["--java"], opts["--drummidi"], f, mp3, mp3_kbps)
                if why:
                    print("MP3 failed %s: %s (the WAV stays)" % (os.path.basename(f), why))
                    continue
                if "--keep_wav" not in switches:
                    os.remove(f)
                check_files[k] = mp3
                print("MP3 %s: %.2f MB (the test beat)" % (os.path.basename(mp3), os.path.getsize(mp3) / float(ZIP_MB)))
        if converted:
            print("MP3s: %d at %d kbps, %.1f MB instead of %.1f MB of WAVs%s" % (converted, mp3_kbps, mp3_mb, wav_mb, " (the WAVs kept)" if "--keep_wav" in switches else ""))
    where = opts["--outdir"] or (folder if batch is not None else os.path.dirname(os.path.abspath(batch_done[0][1]))) if batch_done else None
    limit_mb = zip_mb if "--zip" in switches or "--zip_nowav" in switches or zip_mb else (ask_zip() if batch is not None or test else None)
    if where and limit_mb is not None:
        made = make_zip(where, tag, argv, folder if batch is not None else None,
                        [(m, played_as.get(m)) for m, _ in batch_done] if not opts["--wav"] else [(opts["--wav"], None)],
                        [o for _, o in batch_done], dms, rep, opts, None if by_name else bpm, limit_mb, "--zip_nowav" in switches,
                        rel if two else None, check_files + ([kit_file] if kit_file and os.path.isfile(kit_file) else []))
        for z in made:
            print("Zip: %s (%.1f MB)" % (z, os.path.getsize(z) / float(ZIP_MB)))
        ready = list(made)
    else:
        ready = []
    if test:
        results = results_zip([d for d in (where, opts["--outdir"] or ".") if d])
        if results:
            ready.append(results)
    if opts["--zip_outdir"]:
        copy_zips(ready, opts["--zip_outdir"])
    if batch is not None:
        made = len(batch_done) - kept
        print("Succeeded: %d of %d MIDI files rendered%s%s" % (made, len(batch), ", %d kept from before" % kept if kept else "",
                                                             ", %d silent (left out)" % silent if silent else ""))
    else:
        print("Succeeded: %s" % batch_done[0][1])


if __name__ == "__main__":
    main(sys.argv[1:])
