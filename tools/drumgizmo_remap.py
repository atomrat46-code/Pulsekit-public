"""drumgizmo_remap.py <in.mid> <out.mid> <midimap.xml>: notes the kit's MIDI map lacks moved to its nearest drum
(a floor tom to the next tom, a china to a crash...), so a DrumGizmo render and the MIDI to score it against agree."""
import sys, re, struct
src, dst, mapf = sys.argv[1:4]
have = set(int(n) for n in re.findall(r'note="(\d+)"', open(mapf).read()))
NEAR = {35: [36], 37: [38, 40], 39: [38], 40: [38, 37], 41: [43, 45], 43: [41, 45], 45: [47, 43], 47: [45, 48], 48: [47, 50, 45], 50: [48, 47, 45],
        42: [44], 44: [42], 46: [42], 49: [57, 52, 55, 54], 57: [49, 52, 55, 54], 52: [49, 57, 54], 55: [49, 57, 54], 54: [49, 57],
        51: [59, 53], 59: [51], 53: [51], 81: [53, 51]}
def to(n):
    if n in have: return n
    for m in NEAR.get(n, []):
        if m in have: return m
    return n
d = bytearray(open(src, "rb").read())
i = 8 + struct.unpack(">I", d[4:8])[0]
moved = {}
def vl(p):
    v = 0
    while True:
        b = d[p]; p += 1; v = (v << 7) | (b & 0x7F)
        if b < 0x80: return v, p
while i + 8 <= len(d):
    kind, size = bytes(d[i:i+4]), struct.unpack(">I", d[i+4:i+8])[0]
    p, end, status = i + 8, i + 8 + size, 0
    while kind == b"MTrk" and p < end:
        _, p = vl(p); b = d[p]
        if b == 0xFF:
            n, p = vl(p + 2); p += n; continue
        if b in (0xF0, 0xF7):
            n, p = vl(p + 1); p += n; continue
        if b & 0x80: status = b; p += 1
        hi = status & 0xF0
        if hi in (0xC0, 0xD0): p += 1; continue
        if hi in (0x80, 0x90, 0xA0):
            n = d[p]; m = to(n)
            if m != n: d[p] = m; moved[n] = m
        p += 2
    i += 8 + size
open(dst, "wb").write(bytes(d))
print("moved", moved)
