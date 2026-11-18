#!/usr/bin/env python3
"""setup_demucs.py: Demucs for DrumMidi's "Separate drums first" (--separate), on a computer.

Makes a Python of its own in ~/.pulsekit/demucs (nothing else on the computer changes), installs PyTorch,
Demucs and soundfile into it, and fetches Demucs's htdemucs model (about 80 MB) by separating a second of
silence. DrumMidi finds it there by itself. About 1-3 GB; a song then takes about half its length on a
4-core processor, seconds with an NVIDIA graphics card (--gpu).

  python tools/setup_demucs.py          PyTorch for the processor
  python tools/setup_demucs.py --gpu    PyTorch for an NVIDIA graphics card (CUDA)

In Pulsekit: PyJav, Browse file: setup_demucs.py, --gpu in the Arguments (for an NVIDIA card), Run. Its steps
show in Output as they go; the PyTorch download takes a few minutes (most with --gpu).

Needs Python 3.9 or later and the internet. Run again to repair or update.
"""
import os
import struct
import subprocess
import sys
import tempfile
import time
import venv

HOME = os.path.join(os.path.expanduser("~"), ".pulsekit", "demucs")
WIN = os.name == "nt"
PY = os.path.join(HOME, "Scripts", "python.exe") if WIN else os.path.join(HOME, "bin", "python")


def run(*cmd):
    """Runs a step, saying every 30 s that it is still going: pip prints nothing while it downloads PyTorch
    (2.5 GB with --gpu) when not in a terminal, and Pulsekit's PyJav stops a script silent for 10 minutes."""
    print("> " + " ".join(cmd), flush=True)
    p = subprocess.Popen(list(cmd))
    start = time.time()
    while True:
        try:
            code = p.wait(timeout=30)
            break
        except subprocess.TimeoutExpired:
            print("  ... still working (%d min)" % ((time.time() - start) // 60), flush=True)
    if code:
        raise subprocess.CalledProcessError(code, list(cmd))


def silence(path, seconds=1.0, sr=44100):
    n = int(seconds * sr)
    data = b"\0\0\0\0" * n
    with open(path, "wb") as f:
        f.write(b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 2, sr, sr * 4, 4, 16)
                + b"data" + struct.pack("<I", len(data)) + data)


def main():
    gpu = "--gpu" in sys.argv
    if sys.version_info < (3, 9):
        sys.exit("Python 3.9 or later is needed (this is %d.%d)" % sys.version_info[:2])
    if not os.path.isfile(PY):
        print("Making a Python of its own in " + HOME, flush=True)
        venv.create(HOME, with_pip=True)
    run(PY, "-m", "pip", "install", "--upgrade", "pip")
    index = "https://download.pytorch.org/whl/cu126" if gpu else "https://download.pytorch.org/whl/cpu"
    run(PY, "-m", "pip", "install", "--upgrade", "--index-url", index, "torch", "torchaudio")
    run(PY, "-m", "pip", "install", "--upgrade", "demucs", "soundfile")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "silence.wav")
        silence(wav)
        print("Fetching the htdemucs model...", flush=True)
        run(PY, "-m", "demucs", "--two-stems=drums", "-n", "htdemucs", "-o", tmp, wav)
        if not os.path.isfile(os.path.join(tmp, "htdemucs", "silence", "drums.wav")):
            sys.exit("Demucs ran but made no drums stem")
    dev = subprocess.run([PY, "-c", "import torch; print('NVIDIA graphics card' if torch.cuda.is_available() else 'processor')"],
                         capture_output=True, text=True).stdout.strip()
    print("Ready: DrumMidi's Separate drums first (--separate) uses " + PY + " (on the " + dev + ")")


if __name__ == "__main__":
    main()
