"""MIDIUtil-compatible MIDI writer for Pulsekit's Python runner."""
from __future__ import annotations
import struct

MAJOR = 0
MINOR = 1
SHARPS = 0
FLATS = 1


def _vlq(n):
    n = max(0, int(n))
    buf = [n & 0x7F]
    n >>= 7
    while n:
        buf.append((n & 0x7F) | 0x80)
        n >>= 7
    return bytes(reversed(buf))


class MIDIFile:
    def __init__(
        self,
        numTracks=1,
        removeDuplicates=True,
        deinterleave=True,
        adjust_origin=False,
        file_format=1,
        ticks_per_quarternote=960,
        eventtime_is_ticks=False,
    ):
        self.ticks_per_quarternote = int(ticks_per_quarternote) or 960
        self.eventtime_is_ticks = bool(eventtime_is_ticks)
        self.file_format = 1 if file_format not in (0, 1, 2) else file_format
        self.numTracks = max(1, int(numTracks))
        self.removeDuplicates = removeDuplicates
        self.tracks = [[] for _ in range(self.numTracks)]

    def _tick(self, time):
        if self.eventtime_is_ticks:
            return max(0, int(time))
        return max(0, int(round(float(time) * self.ticks_per_quarternote)))

    def _push(self, track, tick, payload):
        tr = self.tracks[int(track) % self.numTracks]
        item = (int(tick), bytes(payload))
        if self.removeDuplicates and item in tr:
            return
        tr.append(item)

    def addNote(self, track, channel, pitch, time, duration, volume, annotation=None):
        ch = int(channel) & 0x0F
        pitch = max(0, min(127, int(pitch)))
        vel = max(0, min(127, int(volume)))
        start = self._tick(time)
        dur = self._tick(duration) if not self.eventtime_is_ticks else max(1, int(duration))
        if dur <= 0:
            dur = 1
        self._push(track, start, bytes([0x90 | ch, pitch, vel]))
        self._push(track, start + dur, bytes([0x80 | ch, pitch, 0]))

    def addTempo(self, track, time, tempo):
        us = int(round(60_000_000 / max(1.0, float(tempo))))
        data = bytes([(us >> 16) & 0xFF, (us >> 8) & 0xFF, us & 0xFF])
        self._push(track, self._tick(time), bytes([0xFF, 0x51, 3]) + data)

    def addTrackName(self, track, time, trackName):
        name = str(trackName).encode("latin-1", "replace")
        self._push(track, self._tick(time), bytes([0xFF, 0x03, len(name)]) + name)

    def addProgramChange(self, track, channel, time, program):
        ch = int(channel) & 0x0F
        self._push(track, self._tick(time), bytes([0xC0 | ch, int(program) & 0x7F]))

    def addControllerEvent(self, track, channel, time, controller_number, parameter):
        ch = int(channel) & 0x0F
        self._push(
            track,
            self._tick(time),
            bytes([0xB0 | ch, int(controller_number) & 0x7F, int(parameter) & 0x7F]),
        )

    def addPitchWheelEvent(self, track, channel, time, pitchWheelValue):
        ch = int(channel) & 0x0F
        v = max(0, min(16383, int(pitchWheelValue)))
        self._push(track, self._tick(time), bytes([0xE0 | ch, v & 0x7F, (v >> 7) & 0x7F]))

    def addTimeSignature(self, track, time, numerator, denominator, clocks_per_tick, notes_per_quarter=8):
        den_exp = 0
        d = int(denominator)
        while d > 1:
            d //= 2
            den_exp += 1
        self._push(
            track,
            self._tick(time),
            bytes([0xFF, 0x58, 4, int(numerator) & 0xFF, den_exp & 0xFF, int(clocks_per_tick) & 0xFF, int(notes_per_quarter) & 0xFF]),
        )

    def addKeySignature(self, track, time, accidentals, accidental_type, mode, annotation=None):
        acc = int(accidentals) & 0xFF
        md = 0 if mode == MAJOR else 1
        self._push(track, self._tick(time), bytes([0xFF, 0x59, 2, acc, md]))

    def addSysEx(self, track, time, manID, payload):
        data = bytes([int(manID) & 0x7F]) + bytes(payload) + b"\xf7"
        self._push(track, self._tick(time), bytes([0xF0, len(data)]) + data)

    def makeTracksIndependent(self):
        return None

    def writeFile(self, fileHandle):
        chunks = []
        for events in self.tracks:
            events = sorted(events, key=lambda e: e[0])
            body = bytearray()
            last = 0
            for tick, payload in events:
                body += _vlq(tick - last)
                body += payload
                last = tick
            body += _vlq(0)
            body += bytes([0xFF, 0x2F, 0x00])
            chunks.append(b"MTrk" + struct.pack(">I", len(body)) + bytes(body))
        header = b"MThd" + struct.pack(
            ">IHHH", 6, self.file_format, len(self.tracks), self.ticks_per_quarternote
        )
        fileHandle.write(header + b"".join(chunks))

    def close(self):
        return None


MidiFile = MIDIFile
