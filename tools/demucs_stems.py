"""demucs_stems.py <bench out> <dest> [python]: for kit_bench.py score on separated drums (Demucs must be installed in that Python, default python3). every WAV of every kit folder split with Demucs (htdemucs, drums and the rest);
<dest> gets the same folders with each WAV replaced by its drums stem (the played MIDI and kit.kit copied as they are)."""
import glob, os, shutil, subprocess, sys
src, dest = sys.argv[1], sys.argv[2]
PY = sys.argv[3] if len(sys.argv) > 3 else "python3"
work = os.path.join(dest, "_demucs")
for kitdir in sorted(d for d in glob.glob(os.path.join(src, "*")) if os.path.isdir(d)):
    name = os.path.basename(kitdir)
    out = os.path.join(dest, name)
    os.makedirs(out, exist_ok=True)
    wavs = []
    for f in sorted(glob.glob(os.path.join(kitdir, "*"))):
        if f.endswith(".wav"):
            wavs.append(f)
        elif os.path.isfile(f):
            shutil.copy(f, out)
    # One Demucs call for the kit's WAVs (the model loaded once).
    subprocess.run([PY, "-m", "demucs", "--two-stems", "drums", "-n", "htdemucs", "-j", "4", "-o", work] + wavs, check=True)
    for f in wavs:
        stem = os.path.join(work, "htdemucs", os.path.splitext(os.path.basename(f))[0], "drums.wav")
        shutil.copy(stem, os.path.join(out, os.path.basename(f)))
shutil.rmtree(work, ignore_errors=True)
print("stems in", dest)
