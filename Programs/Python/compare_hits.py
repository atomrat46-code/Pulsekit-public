"""
compare_hits.py: how closely drum hits line up, per drum family (kick, snare, cymbals, toms).
The same comparison as Pulsekit's File > Compare Hits page and Programs/Java/CompareHits.java.

  python compare_hits.py <input.wav> <drums.mid> [song.mid] [--log <logfile>]
    MIDI (and song) against onsets heard in the original WAV, and the song against the MIDI.
  python compare_hits.py <drums.mid> <song.mid> [--log <logfile>]
    The song (Export > Song MIDI) against the source MIDI: what Pulsekit's import changed.

  --log <logfile>  also writes the results to this text file. --log alone writes
                   CompareHits_test_results.txt.

Compares hit times, not sound. Hits within 50 ms match, after the best shift within 100 ms.
Ends with suggestions for DrumMidi's switches (--sens, --hat, --ride, --crash) from the results.
When the MIDI carries DrumMidi_CRT's settings (it writes them into its MIDI), the suggestions name
the values it used and end with a "Next run:" argument line.
Needs numpy.
"""
import os
import struct
import sys
import time
import wave

import numpy as np

FAMILIES = ["Kick", "Snare", "Cymbals", "Toms"]
KICK, SNARE, CYMBAL, TOM = range(4)
TOLERANCE = 0.05
FAMILY = {35: KICK, 36: KICK, 37: SNARE, 38: SNARE, 39: SNARE, 40: SNARE,
          42: CYMBAL, 44: CYMBAL, 46: CYMBAL, 49: CYMBAL, 51: CYMBAL, 52: CYMBAL,
          53: CYMBAL, 55: CYMBAL, 57: CYMBAL, 59: CYMBAL,
          41: TOM, 43: TOM, 45: TOM, 47: TOM, 48: TOM, 50: TOM}
LEGEND = ("matched: hits within 50 ms of each other. precision: tested hits that match. "
          "recall: reference hits that are found. timing: median distance of matched hits. "
          "Against a WAV, recall is shown for cymbals only, because the kick and snare bands "
          "also hear bass and guitar.")


def var_len(d, p):
    v = 0
    while p < len(d):
        b = d[p]
        p += 1
        v = (v << 7) | (b & 0x7F)
        if b < 0x80:
            break
    return v, p


def midi_hits(path, kinds=None):
    """Hit times in seconds, sorted, one array per family. Follows tempo changes.
    With `kinds` (a list of three), also counts hat, ride and crash notes into it."""
    if not os.path.isfile(path):
        raise FileNotFoundError("No such file: %s" % os.path.basename(path))
    d = open(path, "rb").read()
    ppq = struct.unpack(">H", d[12:14])[0] or 480
    i = 8 + struct.unpack(">I", d[4:8])[0]
    notes, tempos = [], []
    while i + 8 <= len(d):
        ln = struct.unpack(">I", d[i + 4:i + 8])[0]
        is_track = d[i:i + 4] == b"MTrk"
        p, end = i + 8, min(len(d), i + 8 + ln)
        i = end
        if not is_track:
            continue
        tick, status = 0, 0
        while p < end:
            dt, p = var_len(d, p)
            tick += dt
            if p >= end:
                break
            b = d[p]
            if b == 0xFF:
                kind = d[p + 1]
                ln2, at = var_len(d, p + 2)
                if kind == 0x51 and ln2 >= 3:
                    tempos.append((tick, int.from_bytes(d[at:at + 3], "big")))
                p = at + ln2
                continue
            if b in (0xF0, 0xF7):
                ln2, at = var_len(d, p + 1)
                p = at + ln2
                continue
            if b & 0x80:
                status = b
                p += 1
            kind = status & 0xF0
            size = 1 if kind in (0xC0, 0xD0) else 2
            if p + size > end:
                break
            if kind == 0x90 and d[p + 1] > 0:
                notes.append((tick, d[p] & 0x7F))
            p += size
    tempos.sort()

    def seconds(tick):
        sec, at, tempo = 0.0, 0, 500000
        for t, v in tempos:
            if t >= tick:
                break
            sec += (t - at) * tempo / 1e6 / ppq
            at, tempo = t, v
        return sec + (tick - at) * tempo / 1e6 / ppq

    out = [[] for _ in FAMILIES]
    for tick, note in notes:
        f = FAMILY.get(note)
        if f is not None:
            out[f].append(seconds(tick))
        if kinds is not None:
            if note in (42, 44, 46):
                kinds[0] += 1
            elif note in (51, 53, 59):
                kinds[1] += 1
            elif note in (49, 52, 55, 57):
                kinds[2] += 1
    return [np.array(sorted(x)) for x in out]


def read_wav(path):
    w = wave.open(path)
    sr, ch, width = w.getframerate(), w.getnchannels(), w.getsampwidth()
    raw = w.readframes(w.getnframes())
    if width == 2:
        x = np.frombuffer(raw, np.int16).astype(np.float64) / 32768
    elif width == 3:
        b = np.frombuffer(raw, np.uint8).reshape(-1, 3)
        x = ((b[:, 0].astype(np.int32) | (b[:, 1].astype(np.int32) << 8) | (b[:, 2].astype(np.int32) << 16)) << 8 >> 8) / 8388608
    elif width == 1:
        x = (np.frombuffer(raw, np.uint8).astype(np.float64) - 128) / 128
    else:
        x = np.frombuffer(raw, np.int32).astype(np.float64) / 2147483648
    return x.reshape(-1, ch).mean(1), sr


def audio_onsets(x, sr):
    """Onsets per family from three bands: low (kick), mid (snare), high (cymbals). No toms."""
    n, hop = 1024, max(64, sr // 100)
    win = 0.5 - 0.5 * np.cos(2 * np.pi * np.arange(n) / (n - 1))
    frames = np.lib.stride_tricks.sliding_window_view(x, n)[::hop] * win
    spec = np.abs(np.fft.rfft(frames, axis=1))
    out = [np.array([]) for _ in FAMILIES]
    bands = [(KICK, 30, 120, 0.12), (SNARE, 180, 1200, 0.12), (CYMBAL, 7000, 16000, 0.10)]
    for fam, lo, hi, delta in bands:
        a = max(1, int(np.ceil(lo * n / sr)))
        z = min(n // 2, int(np.ceil(min(hi, sr / 2 - 1) * n / sr)))
        e = np.log1p(100 * spec[:, a:z])
        flux = np.maximum(0, np.diff(e, axis=0)).sum(1)
        top = np.sort(flux)[min(len(flux) - 1, int(0.99 * (len(flux) - 1)))]
        if not top > 0:
            continue
        v = flux / top
        k = int(0.15 * sr / hop)
        times = []
        for i in range(1, len(v) - 1):
            if not (v[i] > v[i - 1] and v[i] >= v[i + 1]):
                continue
            if v[i] <= np.median(v[max(0, i - k):min(len(v), i + k)]) + delta:
                continue
            t = (i + 1) * hop / sr
            if not times or t - times[-1] > 0.05:
                times.append(t)
        out[fam] = np.array(times)
    return out


def match(ref, test, tol, errs=None):
    """Greedy one-to-one matching in time order."""
    used = np.zeros(len(test), bool)
    m = 0
    for r in ref:
        k = int(np.searchsorted(test, r))
        best = None
        lo = int(np.searchsorted(test, r - tol, "left"))
        hi = int(np.searchsorted(test, r + tol, "right"))
        for c in range(max(0, min(lo, k - 1)), min(len(test), max(hi, k + 1))):
            if not used[c] and abs(test[c] - r) <= tol:
                if best is None or abs(test[c] - r) < abs(test[best] - r):
                    best = c
        if best is not None:
            used[best] = True
            m += 1
            if errs is not None:
                errs.append(test[best] - r)
    return m


def best_offset(ref, test):
    a = np.sort(np.concatenate([ref[f] for f in range(3)]))
    b = np.sort(np.concatenate([test[f] for f in range(3)]))
    best, best_n = 0.0, -1
    for ms in range(-100, 101):
        n = match(a, b + ms / 1000, 0.02)
        if n > best_n or (n == best_n and abs(ms) < abs(best * 1000)):
            best, best_n = ms / 1000, n
    return best


def compare(ref_name, ref, test_name, test, audio_ref):
    """The table text, and {family: (ref hits, test hits, matched)} for suggestions()."""
    off = best_offset(ref, test)
    rows = {}
    lines = ["%s against %s, shifted %+d ms to line up" % (test_name, ref_name, round(off * 1000)),
             "%-8s %6s %6s %8s %8s %7s %7s" % ("", "ref", "test", "matched", "precis.", "recall", "timing")]
    for f, name in enumerate(FAMILIES):
        a, b = ref[f], test[f]
        if (audio_ref and f == TOM) or (len(a) == 0 and len(b) == 0):
            continue
        errs = []
        m = match(a, b + off, TOLERANCE, errs)
        p = m / len(b) if len(b) else 0
        r = m / len(a) if len(a) else 0
        med = 1000 * float(np.median(np.abs(errs))) if errs else 0
        recall = "%.1f%%" % (100 * r) if (not audio_ref or f == CYMBAL) else "-"
        lines.append("%-8s %6d %6d %8d %7.1f%% %7s %5.1fms" % (name, len(a), len(b), m, 100 * p, recall, med))
        rows[f] = (len(a), len(b), m)
    return "\n".join(lines) + "\n", rows


def midi_bpm(path):
    """The MIDI file's first tempo in BPM, or 120."""
    d = open(path, "rb").read()
    for i in range(len(d) - 5):
        if d[i] == 0xFF and d[i + 1] == 0x51 and d[i + 2] == 0x03:
            us = int.from_bytes(d[i + 3:i + 6], "big")
            if us > 0:
                return 60000000.0 / us
    return 120.0


def drum_midi_settings(path):
    """Settings DrumMidi_CRT writes into its MIDI ("pulsekit-drummidi; key=value; ..."), or {}."""
    d = open(path, "rb").read()
    i = 0
    while True:
        i = d.find(b"\xff\x01", i)
        if i < 0 or i + 3 >= len(d):
            return {}
        ln, at = var_len(d, i + 2)
        text = d[at:at + ln].decode("utf-8", "replace")
        if text.startswith("pulsekit-drummidi"):
            out_ = {}
            for part in text.split(";"):
                if "=" in part:
                    k, v = part.split("=", 1)
                    out_[k.strip()] = v.strip()
            return out_
        i += 2


def fmt(v):
    t = "%.2f" % v
    return t.rstrip("0").rstrip(".")


def plain_name(path):
    import re
    return re.sub(r"[^a-z0-9.]+", "_", os.path.basename(path or "").lower())


def next_args(settings, nxt):
    words = (settings.get("args") or "").split()
    for key, val in nxt.items():
        flag = "--" + key
        if flag in words and words.index(flag) + 1 < len(words):
            words[words.index(flag) + 1] = fmt(val)
        else:
            words += [flag, fmt(val)]
    return " ".join(words)


# Hat, ride and crash are raised no further than this, and a value above it is lowered straight to
# it: DrumMidi's cymbal hits rise steeply above 0.4 (a hard rock mix: 19 crashes at 0.4, 102 at 0.6).
CYMBAL_MAX = 0.4


def step(nxt, keys, was, d, unknown):
    """Moves each switch by d for the next run (raised to no more than CYMBAL_MAX; lowered to no less
    than 0.1, and from above CYMBAL_MAX straight to it) and says so; `unknown` when the values used are not known."""
    if any(v is None for v in was):
        return unknown
    # Above CYMBAL_MAX a step down goes straight to it: a smaller step still leaves the noise.
    to = [max(v, min(CYMBAL_MAX, v + d)) if d > 0 else (CYMBAL_MAX if v > CYMBAL_MAX else max(0.1, v + d)) for v in was]
    for key, v in zip(keys, to):
        nxt[key] = v
    return " (%s %s; try %s)." % ("it was" if len(keys) == 1 else "they were",
                                  ", ".join(fmt(v) for v in was), ", ".join(fmt(v) for v in to))


def suggestions(midi, bpm, vs_wav, song_vs_midi, settings=None, wav_name=None, kinds=None):
    """Hints for DrumMidi's switches, as in Pulsekit's HitCompare.suggestions."""
    settings = settings or {}
    out_ = []
    nxt = {}
    inp = settings.get("input")
    if inp and wav_name and plain_name(inp) != plain_name(wav_name):
        out_.append("The MIDI was made from %s, but it is compared with %s." % (inp, wav_name))
    hits = np.sort(np.concatenate([midi[f] for f in range(len(FAMILIES))]))
    bar_sec = 240.0 / max(30.0, bpm)
    bars = 1.0 if len(hits) < 2 else max(1.0, (hits[-1] - hits[0]) / bar_sec + 1)
    kicks, snares, cymbals = len(midi[KICK]) / bars, len(midi[SNARE]) / bars, len(midi[CYMBAL]) / bars

    def number(key):
        try:
            return float(settings[key])
        except (KeyError, ValueError):
            return None

    def prec(r):
        return r[2] / r[1] if r and r[1] else 0.0

    def rec(r):
        return r[2] / r[0] if r and r[0] else 0.0

    sens = number("sens")
    few = kicks < 1.0 or snares < 0.75
    if few:
        if sens is not None:
            nxt["sens"] = sens + 0.2
        out_.append("--sens should be greater: only %.1f kicks and %.1f snares per bar were found " % (kicks, snares)
                    + ("(it was %s; try %s)." % (fmt(sens), fmt(sens + 0.2)) if sens is not None
                       else "(raise it by about 0.2, e.g. 0.4 -> 0.6)."))
    many = kicks > 7.0 or snares > 6.0
    if many:
        if sens is not None:
            nxt["sens"] = max(0.1, sens - 0.2)
        out_.append("--sens may be too high: %.1f kicks and %.1f snares per bar is a lot " % (kicks, snares)
                    + ("(it was %s; try %s)." % (fmt(sens), fmt(max(0.1, sens - 0.2))) if sens is not None
                       else "(lower it by about 0.2 and compare again)."))
    k = vs_wav.get(KICK) if vs_wav else None
    sn = vs_wav.get(SNARE) if vs_wav else None
    if not few and not many and ((k and k[1] >= 8 and prec(k) < 0.85) or (sn and sn[1] >= 8 and prec(sn) < 0.85)):
        if sens is not None:
            nxt["sens"] = max(0.1, sens - 0.1)
        out_.append("--sens could be lower: %.0f%% of kicks and %.0f%% of snares in the MIDI are not heard in the WAV "
                    % (100 * (1 - prec(k)) if k else 0, 100 * (1 - prec(sn)) if sn else 0)
                    + ("(it was %s; try %s)." % (fmt(sens), fmt(max(0.1, sens - 0.1))) if sens is not None
                       else "(lower it by about 0.1)."))
    cym = ["hat", "ride", "crash"]
    was = [number(x) for x in cym]
    c = vs_wav.get(CYMBAL) if vs_wav else None
    # On a full mix the WAV's top band also hears cymbal wash, guitars and vocals, so cymbal recall
    # stays low even when the MIDI has plenty. The MIDI's own crashes, rides and hats come first.
    crashes = kinds[2] / bars if kinds else 0.0
    hats_rides = (kinds[0] + kinds[1]) / bars if kinds else 0.0
    lowered = False
    if crashes > 0.75:
        lowered = True
        out_.append("--crash may be too high: %.1f crashes per bar, where a crash usually marks a new "
                    "section (about one every 4 to 8 bars)" % crashes
                    + step(nxt, ["crash"], [was[2]], -0.1, ". Lower it by about 0.1."))
    hats = kinds[0] / bars if kinds else 0.0
    rides = kinds[1] / bars if kinds else 0.0
    if hats_rides > 16:
        lowered = True
        out_.append("--hat and --ride may be too high: %.1f hat and ride hits per bar is more than a "
                    "16th-note groove plays" % hats_rides
                    + step(nxt, ["hat", "ride"], was[:2], -0.1, ". Lower them by about 0.1."))
    elif hats > 2 and rides > 2:
        # A drummer keeps time on the hats or the ride; both all the way through is cymbal wash turned into hits.
        lowered = True
        out_.append("--hat and --ride may be too high: the MIDI plays %.1f hats and %.1f rides per bar "
                    "together, where a drummer keeps time on one of them" % (hats, rides)
                    + step(nxt, ["hat", "ride"], was[:2], -0.1, ". Lower them by about 0.1."))
    elif rides > 1.5 and was[1] is not None and was[1] > CYMBAL_MAX:
        # Above CYMBAL_MAX the ride band turns cymbal wash and guitars into a ride on most beats.
        lowered = True
        out_.append("--ride may be too high: %.1f rides per bar, and above %s the ride picks up "
                    "cymbal wash and guitars" % (rides, fmt(CYMBAL_MAX))
                    + step(nxt, ["ride"], [was[1]], -0.1, ". Lower it to %s." % fmt(CYMBAL_MAX)))
    few_cymbals = cymbals < 2.0 and ((c[0] >= 8 and rec(c) < 0.5) if c else vs_wav is None)
    room = was[0] is None or was[1] is None or was[0] < CYMBAL_MAX or was[1] < CYMBAL_MAX
    if not lowered and few_cymbals and room:
        # Hats and ride only, a little at a time: high values turn cymbal wash into a crash on every beat.
        out_.append("--hat and --ride may need more sensitivity: only %.1f cymbal hits per bar" % cymbals
                    + (" (%.0f%% of the WAV's high-band hits; that band also hears cymbal wash and "
                       "guitars, so this is a hint)" % (100 * rec(c)) if c else "")
                    + step(nxt, ["hat", "ride"], was[:2], 0.1,
                           ". Raise them by about 0.1, to no more than %s." % fmt(CYMBAL_MAX)))
    elif not lowered and c and c[1] >= 8 and prec(c) < 0.7:
        out_.append("--hat, --ride and --crash could be lower: %.0f%% of the MIDI's cymbal hits are not "
                    "heard in the WAV" % (100 * (1 - prec(c)))
                    + step(nxt, cym, was, -0.1, ". Lower them by about 0.1."))
    sk = song_vs_midi.get(KICK) if song_vs_midi else None
    ss = song_vs_midi.get(SNARE) if song_vs_midi else None
    if (sk and sk[0] >= 8 and rec(sk) < 0.9) or (ss and ss[0] >= 8 and rec(ss) < 0.9):
        out_.append("Not DrumMidi: the song keeps %.0f%% of the MIDI's kicks and %.0f%% of its snares. "
                    "In Drum Midi Settings, lower \"Merge up to\" or turn off Merge hits, then import again."
                    % (100 * rec(sk) if sk else 100, 100 * rec(ss) if ss else 100))
    if not out_:
        out_.append("No changes suggested: the hits line up well.")
    if nxt:
        out_.append("Next run: " + next_args(settings, nxt))
    return "Suggestions for DrumMidi:\n" + "".join("- %s\n" % l for l in out_)


DEFAULT_LOG = "CompareHits_test_results.txt"
REPORT = []
LOG = {"path": None}


def out(line=""):
    print(line)
    REPORT.append(line)


def finish(last):
    """Writes the --log file, then prints the last line (PyJav reads it as the run's status)."""
    REPORT.append(last)
    if LOG["path"]:
        try:
            parent = os.path.dirname(os.path.abspath(LOG["path"]))
            if parent and not os.path.isdir(parent):
                os.makedirs(parent)
            with open(LOG["path"], "w", encoding="utf-8") as f:
                f.write("\n".join(REPORT) + "\n")
            print("Log: %s" % os.path.abspath(LOG["path"]))
        except Exception as ex:
            print("Could not write the log %s: %s" % (LOG["path"], ex))
    print(last)


def main(args):
    files = []
    i = 0
    while i < len(args):
        a = args[i].strip()
        if a == "--log":
            # --log alone writes CompareHits_test_results.txt.
            nxt = args[i + 1].strip() if i + 1 < len(args) else ""
            named = nxt and not nxt.startswith("--") and not nxt.lower().endswith((".wav", ".wave", ".mid", ".midi"))
            LOG["path"] = nxt if named else DEFAULT_LOG
            i += 2 if named else 1
            continue
        if a:
            files.append(a)
        i += 1
    if len(files) < 2:
        out("Usage: python compare_hits.py <input.wav> <drums.mid> [song.mid] [--log <logfile>]")
        finish("Failed: need a WAV and a MIDI file, or two MIDI files")
        return
    is_wav = files[0].lower().endswith((".wav", ".wave"))
    audio = None
    if is_wav:
        t0 = time.time()
        x, sr = read_wav(files[0])
        audio = audio_onsets(x, sr)
        out("Read %s: %.1f s, onsets found in %d ms" % (os.path.basename(files[0]), len(x) / sr, (time.time() - t0) * 1000))
    at = 1 if is_wav else 0
    kinds = [0, 0, 0]
    midi = midi_hits(files[at], kinds)
    settings = drum_midi_settings(files[at])
    if settings:
        out("DrumMidi settings: %s%s" % (settings.get("args") or "its defaults",
                                          " (made from %s)" % settings["input"] if settings.get("input") else ""))
    song = midi_hits(files[at + 1]) if len(files) > at + 1 else None
    out()
    vs_wav = song_vs_midi = None
    if audio is not None:
        text, vs_wav = compare("WAV", audio, "MIDI", midi, True)
        out(text)
        if song is not None:
            out(compare("WAV", audio, "Song", song, True)[0])
    if song is not None:
        text, song_vs_midi = compare("MIDI", midi, "Song", song, False)
        out(text)
    out(suggestions(midi, midi_bpm(files[at]), vs_wav, song_vs_midi, settings, os.path.basename(files[0]) if is_wav else None, kinds))
    out(LEGEND)
    finish("Succeeded: compared %d files" % len(files))


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except Exception as ex:  # report like the other Pulsekit programs
        finish("Failed: %s" % ex)
