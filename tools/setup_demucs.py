#!/usr/bin/env python3
"""setup_demucs.py: Demucs for DrumMidi's "Separate drums first" (--separate), and Beat This! for DrumAlong's
beats, on a computer.

Usage: python setup_demucs.py [--gpu] [--dir <folder>]

Makes a Python of its own in ~/.pulsekit/demucs, or the folder --dir names (nothing else on the computer
changes; the downloads, Demucs's model and pip's temporary files stay in that folder too), installs PyTorch,
Demucs, soundfile and Beat This! (beat-this) into it, and fetches Demucs's htdemucs model (about 80 MB) by
separating a second of silence and Beat This!'s model (77 MB). DrumMidi finds it there by itself. About 1-3 GB; a song then takes about half its length on a
4-core processor, seconds with an NVIDIA graphics card (--gpu).

  python tools/setup_demucs.py          PyTorch for the processor
  python tools/setup_demucs.py --gpu    PyTorch for an NVIDIA graphics card (CUDA)
  python tools/setup_demucs.py --dir D:\Tools\demucs    in that folder (a drive with room: 1-3 GB)

DrumMidi finds it by ~/.pulsekit/demucs.txt, which names the Python in it (written when the setup is done).

In Pulsekit: PyJav, Browse file: setup_demucs.py, --gpu and/or --dir <folder> in the Arguments, Run. Its steps
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

PULSEKIT = os.path.join(os.path.expanduser("~"), ".pulsekit")
HOME = os.path.join(PULSEKIT, "demucs")
if "--dir" in sys.argv and sys.argv.index("--dir") + 1 < len(sys.argv):
    HOME = os.path.abspath(sys.argv[sys.argv.index("--dir") + 1])
WIN = os.name == "nt"
PY = os.path.join(HOME, "Scripts", "python.exe") if WIN else os.path.join(HOME, "bin", "python")
# Big downloads and caches inside HOME, not on the system drive: Demucs's model (torch hub, Hugging Face),
# pip's temporary files (pip's own cache is not used).
ENV = dict(os.environ)
for k, sub in (("TORCH_HOME", "torch-cache"), ("HF_HOME", "hf-cache"), ("TMP", "tmp"), ("TEMP", "tmp"), ("TMPDIR", "tmp")):
    ENV[k] = os.path.join(HOME, sub)


def run(*cmd):
    """Runs a step, saying every 30 s that it is still going: pip prints nothing while it downloads PyTorch
    (2.5 GB with --gpu) when not in a terminal, and Pulsekit's PyJav stops a script silent for 10 minutes."""
    print("> " + " ".join(cmd), flush=True)
    p = subprocess.Popen(list(cmd), env=ENV)
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
    for sub in ("torch-cache", "hf-cache", "tmp"):
        os.makedirs(os.path.join(HOME, sub), exist_ok=True)
    run(PY, "-m", "pip", "install", "--no-cache-dir", "--upgrade", "pip")
    index = "https://download.pytorch.org/whl/cu126" if gpu else "https://download.pytorch.org/whl/cpu"
    run(PY, "-m", "pip", "install", "--no-cache-dir", "--upgrade", "--index-url", index, "torch", "torchaudio")
    run(PY, "-m", "pip", "install", "--no-cache-dir", "--upgrade", "demucs", "soundfile", "beat-this")
    with tempfile.TemporaryDirectory(dir=os.path.join(HOME, "tmp")) as tmp:
        wav = os.path.join(tmp, "silence.wav")
        silence(wav)
        print("Fetching the htdemucs model...", flush=True)
        run(PY, "-m", "demucs", "--two-stems=drums", "-n", "htdemucs", "-o", tmp, wav)
        if not os.path.isfile(os.path.join(tmp, "htdemucs", "silence", "drums.wav")):
            sys.exit("Demucs ran but made no drums stem")
        print("Fetching Beat This!'s model...", flush=True)
        run(PY, "-c", "import sys; from beat_this.inference import File2Beats; File2Beats(checkpoint_path='final0', device='cpu', dbn=False)(sys.argv[1])", wav)
    dev = subprocess.run([PY, "-c", "import torch; print('NVIDIA graphics card' if torch.cuda.is_available() else 'processor')"],
                         capture_output=True, text=True, env=ENV).stdout.strip()
    # Where DrumMidi finds it.
    os.makedirs(PULSEKIT, exist_ok=True)
    with open(os.path.join(PULSEKIT, "demucs.txt"), "w", encoding="utf-8") as f:
        f.write(PY + "\n")
    print("Ready: DrumMidi's Separate drums first (--separate) and DrumAlong's beats use " + PY + " (on the " + dev + ")")


if __name__ == "__main__":
    main()
