#!/usr/bin/env python3
"""Pulsekit drum MIDI automation. Writes a GM drum .mid from style, tempo and length."""
import argparse
import os
import struct

NOTES = {
    "kick": 36, "dkick": 35, "snare": 38, "clap": 39, "rim": 37,
    "chh": 42, "ohh": 46, "crash": 49, "ride": 51,
    "ltom": 41, "mtom": 47, "htom": 50,
}

STYLES = {
    "house": {"kick": "X---X---X---X---", "snare": "----x-------x---", "chh": "x-x-x-x-x-x-x-x-"},
    "techno": {"kick": "X---X---X---X---", "chh": "xxxxxxxxxxxxxxxx", "ride": "x-x-x-x-x-x-x-x-"},
    "hiphop": {"kick": "X------x--x-----", "snare": "----X-------X---", "chh": "x-x-x-x-x-x-x-x-"},
    "trap": {"kick": "X-----x------x-x", "snare": "----X-------X---", "chh": "x-xxx-x-x-xxx-x-"},
    "rock": {"kick": "X-------X-X-----", "snare": "----X-------X---", "chh": "x-x-x-x-x-x-x-x-", "crash": "x-------x-------"},
    "metal": {"kick": "X-X-X-X-X-X-X-X-", "snare": "----X-------X---", "chh": "x-x-x-x-x-x-x-x-"},
    "progmetal": {"kick": "X--X--X-X--X--X-", "dkick": "-XX-XX-X-XX-XX-X", "snare": "----X-------X---", "chh": "x-x-x-x-x-x-x-x-"},
    "pop": {"kick": "X-------X-------", "snare": "----X-------X---", "clap": "----X-------X---", "chh": "x-x-x-x-x-x-x-x-"},
}

def vel(ch):
    return 127 if ch == "X" else 100 if ch == "x" else 64 if ch == "o" else 0

def vlq(n):
    n = max(0, int(n))
    buf = [n & 0x7F]
    n >>= 7
    while n:
        buf.append((n & 0x7F) | 0x80)
        n >>= 7
    return bytes(reversed(buf))

def write_mid(path, hits, bpm, bars, swing):
    tpq = 480
    step = tpq // 4
    us = int(60_000_000 / max(40, min(240, bpm)))
    events = [(0, bytes([0xFF, 0x51, 0x03, (us >> 16) & 255, (us >> 8) & 255, us & 255]))]
    for bar in range(bars):
        for name, row in hits.items():
            note = NOTES[name]
            for s, ch in enumerate(row):
                v = vel(ch)
                if v <= 0:
                    continue
                tick = bar * 16 * step + s * step
                if s % 2 == 1 and swing:
                    tick += int(step * swing / 100 * 0.58)
                events.append((tick, bytes([0x99, note, v])))
                events.append((tick + 80, bytes([0x89, note, 0])))
    events.sort(key=lambda e: e[0])
    body = bytearray()
    last = 0
    for tick, data in events:
        body += vlq(tick - last)
        body += data
        last = tick
    end = bars * 16 * step
    body += vlq(max(0, end - last))
    body += bytes([0xFF, 0x2F, 0x00])
    track = b"MTrk" + struct.pack(">I", len(body)) + bytes(body)
    head = b"MThd" + struct.pack(">IHHH", 6, 0, 1, tpq)
    with open(path, "wb") as f:
        f.write(head + track)
    print("wrote", os.path.abspath(path), "bpm", bpm, "bars", bars, "style", hits and "ok")

def main():
    p = argparse.ArgumentParser(description="Pulsekit drum MIDI automation")
    p.add_argument("--bpm", type=int, default=124)
    p.add_argument("--style", default="house")
    p.add_argument("--bars", type=int, default=4)
    p.add_argument("--swing", type=int, default=12)
    p.add_argument("--out", default="beat.mid")
    args = p.parse_args()
    style = args.style.lower().replace(" ", "")
    hits = STYLES.get(style) or STYLES["house"]
    write_mid(args.out, hits, args.bpm, max(1, args.bars), max(0, args.swing))

if __name__ == "__main__":
    main()
