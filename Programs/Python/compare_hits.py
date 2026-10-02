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


def midi_hits(path):
    """Hit times in seconds, sorted, one array per family. Follows tempo changes."""
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


def suggestions(midi, bpm, vs_wav, song_vs_midi):
    """Hints for DrumMidi's switches, as in Pulsekit's HitCompare.suggestions."""
    out_ = []
    hits = np.sort(np.concatenate([midi[f] for f in range(len(FAMILIES))]))
    bar_sec = 240.0 / max(30.0, bpm)
    bars = 1.0 if len(hits) < 2 else max(1.0, (hits[-1] - hits[0]) / bar_sec + 1)
    kicks, snares, cymbals = len(midi[KICK]) / bars, len(midi[SNARE]) / bars, len(midi[CYMBAL]) / bars

    def prec(r):
        return r[2] / r[1] if r and r[1] else 0.0

    def rec(r):
        return r[2] / r[0] if r and r[0] else 0.0

    few = kicks < 1.0 or snares < 0.75
    if few:
        out_.append("--sens should be greater: only %.1f kicks and %.1f snares per bar were found "
                    "(raise it by about 0.2, e.g. 0.4 -> 0.6)." % (kicks, snares))
    many = kicks > 7.0 or snares > 6.0
    if many:
        out_.append("--sens may be too high: %.1f kicks and %.1f snares per bar is a lot "
                    "(lower it by about 0.2 and compare again)." % (kicks, snares))
    k = vs_wav.get(KICK) if vs_wav else None
    sn = vs_wav.get(SNARE) if vs_wav else None
    if not few and not many and ((k and k[1] >= 8 and prec(k) < 0.85) or (sn and sn[1] >= 8 and prec(sn) < 0.85)):
        out_.append("--sens could be lower: %.0f%% of kicks and %.0f%% of snares in the MIDI are not "
                    "heard in the WAV (lower it by about 0.1)." % (100 * (1 - prec(k)) if k else 0, 100 * (1 - prec(sn)) if sn else 0))
    c = vs_wav.get(CYMBAL) if vs_wav else None
    if (c and c[0] >= 8 and rec(c) < 0.5) or (vs_wav is None and cymbals < 1.0):
        if c:
            out_.append("--hat, --ride and --crash need more sensitivity: the MIDI has only %.0f%% of the "
                        "cymbal hits heard in the WAV (%.1f per bar). Raise them, e.g. to 0.8, or leave them out "
                        "to follow --sens." % (100 * rec(c), cymbals))
        else:
            out_.append("--hat, --ride and --crash may need more sensitivity: only %.1f cymbal hits per bar." % cymbals)
    elif c and c[1] >= 8 and prec(c) < 0.7:
        out_.append("--hat, --ride and --crash could be lower: %.0f%% of the MIDI's cymbal hits are not "
                    "heard in the WAV." % (100 * (1 - prec(c))))
    sk = song_vs_midi.get(KICK) if song_vs_midi else None
    ss = song_vs_midi.get(SNARE) if song_vs_midi else None
    if (sk and sk[0] >= 8 and rec(sk) < 0.9) or (ss and ss[0] >= 8 and rec(ss) < 0.9):
        out_.append("Not DrumMidi: the song keeps %.0f%% of the MIDI's kicks and %.0f%% of its snares. "
                    "In Drum Midi Settings, lower \"Merge up to\" or turn off Merge hits, then import again."
                    % (100 * rec(sk) if sk else 100, 100 * rec(ss) if ss else 100))
    if not out_:
        out_.append("No changes suggested: the hits line up well.")
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
    midi = midi_hits(files[at])
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
    out(suggestions(midi, midi_bpm(files[at]), vs_wav, song_vs_midi))
    out(LEGEND)
    finish("Succeeded: compared %d files" % len(files))


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except Exception as ex:  # report like the other Pulsekit programs
        finish("Failed: %s" % ex)
