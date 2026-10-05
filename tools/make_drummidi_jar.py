#!/usr/bin/env python3
"""Builds Programs/Java/DrumMidi_CRT.jar: DrumMidi_CRT with JLayer's MP3 decoder, for PyJav.

PyJav on Android turns a jar's classes into DEX and loads only those, so files inside the jar
cannot be read as resources. JLayer's tables (sfd.ser, l3reorder.ser) therefore go into a
generated class, JlTables, which DrumMidi_CRT hands to JLayer through its JavaLayerHook.

The jar is written with fixed timestamps and in a fixed order, so it changes only when
DrumMidi_CRT.java (or JLayer) does. android/build.sh and desktop/build.sh run this script.

  python3 tools/make_drummidi_jar.py
"""
import base64
import os
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "Programs", "Java", "DrumMidi_CRT.java")
JLAYER = os.path.join(ROOT, "shared", "libs", "jlayer-1_0_1.jar")
OUT = os.path.join(ROOT, "Programs", "Java", "DrumMidi_CRT.jar")
TABLES = ("javazoom/jl/decoder/sfd.ser", "javazoom/jl/decoder/l3reorder.ser")
STAMP = (2026, 1, 1, 0, 0, 0)


def tables_source(lib):
    """JlTables.java: bytes(name) gives a table by its name (with or without a folder)."""
    lines = [
        "/** JLayer's tables for DrumMidi_CRT.jar (made by tools/make_drummidi_jar.py; do not edit). */",
        "public final class JlTables {",
        "  private JlTables() {}",
        "",
        "  public static byte[] bytes(String name) {",
        "    String n = name == null ? \"\" : name.replace('\\\\', '/');",
        "    n = n.substring(n.lastIndexOf('/') + 1);",
    ]
    for path in TABLES:
        data = base64.b64encode(lib.read(path)).decode("ascii")
        chunks = [data[i:i + 8000] for i in range(0, len(data), 8000)]
        joined = " + ".join('"%s"' % c for c in chunks)
        lines.append("    if (n.equals(\"%s\")) return decode(%s);" % (path.rsplit("/", 1)[1], joined))
    lines += [
        "    return null;",
        "  }",
        "",
        "  /** Base64, read without java.util.Base64 (Android before API 26 has none). */",
        "  static byte[] decode(String s) {",
        "    String abc = \"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\";",
        "    int pad = s.endsWith(\"==\") ? 2 : s.endsWith(\"=\") ? 1 : 0;",
        "    byte[] out = new byte[s.length() / 4 * 3 - pad];",
        "    int o = 0;",
        "    for (int i = 0; i < s.length(); i += 4) {",
        "      int v = 0;",
        "      for (int j = 0; j < 4; j++) {",
        "        char c = s.charAt(i + j);",
        "        v = (v << 6) | (c == '=' ? 0 : abc.indexOf(c));",
        "      }",
        "      for (int j = 2; j >= 0; j--) {",
        "        if (o + 2 - j < out.length) out[o + 2 - j] = (byte) (v >> (8 * j));",
        "      }",
        "      o += 3;",
        "    }",
        "    return out;",
        "  }",
        "}",
        "",
    ]
    return "\n".join(lines)


def main():
    with zipfile.ZipFile(JLAYER) as lib, tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "src")
        classes = os.path.join(tmp, "classes")
        os.makedirs(src)
        os.makedirs(classes)
        with open(os.path.join(src, "JlTables.java"), "w") as f:
            f.write(tables_source(lib))
        cmd = ["javac", "-nowarn", "-encoding", "UTF-8", "--release", "8", "-d", classes,
               SOURCE, os.path.join(src, "JlTables.java")]
        done = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if done.returncode != 0:
            sys.stderr.write(done.stdout)
            sys.exit("DrumMidi_CRT.jar: compile failed")
        entries = {}
        for dirpath, _, files in os.walk(classes):
            for name in files:
                full = os.path.join(dirpath, name)
                entries[os.path.relpath(full, classes).replace(os.sep, "/")] = open(full, "rb").read()
        # The decoder only: JLayer's player and converter need javax.sound, which Android lacks.
        for name in lib.namelist():
            if name.startswith("javazoom/jl/decoder/") and name.endswith(".class"):
                entries[name] = lib.read(name)
        manifest = b"Manifest-Version: 1.0\r\nMain-Class: DrumMidi_CRT\r\nCreated-By: tools/make_drummidi_jar.py\r\n\r\n"
        tmp_out = OUT + ".tmp"
        with zipfile.ZipFile(tmp_out, "w", zipfile.ZIP_DEFLATED) as jar:
            for name, data in [("META-INF/MANIFEST.MF", manifest)] + sorted(entries.items()):
                info = zipfile.ZipInfo(name, STAMP)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                jar.writestr(info, data)
    if os.path.isfile(OUT) and open(OUT, "rb").read() == open(tmp_out, "rb").read():
        os.remove(tmp_out)
        return
    os.replace(tmp_out, OUT)
    print("Wrote " + os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
