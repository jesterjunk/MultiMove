#!/usr/bin/env python3
"""Build and test MultiMove without a game installation or Maven.

Requires Python 3.8+ and a Java 8+ runtime. The small, pinned compile-only
dependencies are downloaded to .deps; none is shipped in the mod JAR.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
VERSION = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
DEPS = {
    "server-modlauncher-0.47.jar": (
        "https://gotti.no-ip.org/maven/repository/org/gotti/wurmunlimited/server-modlauncher/0.47/server-modlauncher-0.47.jar",
        "44f7c9adc2dfbe2de45d7bc22a6ef8448549cde3f1f164b5c59a094948e6bacf"),
    "javassist-3.23.1-GA.jar": (
        "https://repo.maven.apache.org/maven2/org/javassist/javassist/3.23.1-GA/javassist-3.23.1-GA.jar",
        "d2b14c09763523374624f32a09d6e31fcb174082a97addb5ae2d580b474fd806"),
    "ecj-3.26.0.jar": (
        "https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.26.0/ecj-3.26.0.jar",
        "ac0ba5876eaf7ebb47749a0d1be179c51f194b9dd0b875d1c09e1b530f5a2db5"),
}


def run(command):
    subprocess.run([str(part) for part in command], cwd=ROOT, check=True)


def dependencies():
    folder = ROOT / ".deps"
    folder.mkdir(exist_ok=True)
    result = {}
    for name, (url, checksum) in DEPS.items():
        path = folder / name
        if not path.exists():
            print("Downloading " + name, flush=True)
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest() != checksum:
                raise RuntimeError("Checksum mismatch: " + name)
            path.write_bytes(data)
        if hashlib.sha256(path.read_bytes()).hexdigest() != checksum:
            raise RuntimeError("Checksum mismatch: " + str(path))
        result[name] = path
    return result


def compile_java(java, compiler, sources, output, classpath):
    output.mkdir(parents=True, exist_ok=True)
    run([java, "-jar", compiler, "-8", "-proc:none", "-encoding", "UTF-8",
         "-warn:none", "-classpath", os.pathsep.join(map(str, classpath)),
         "-d", output] + sorted(sources))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--test", action="store_true", help="run transformed behavioral tests")
    parser.add_argument("--api", nargs="+", type=Path,
                        help="compile and verify the patch against supplied server/common JARs")
    parser.add_argument("--quantity-packet", type=Path,
                        help="also test a packet produced by the client companion's tests")
    options = parser.parse_args()
    java = shutil.which("java")
    if java is None:
        raise RuntimeError("Java 8 or newer must be installed and on PATH")
    deps = dependencies()
    build = ROOT / "build"
    classes = build / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    compile_deps = [deps[name] for name in DEPS if not name.startswith("ecj-")]
    compile_java(java, deps["ecj-3.26.0.jar"],
                 [str(p) for p in (ROOT / "src/main/java").rglob("*.java")],
                 classes, compile_deps)
    for resource in (ROOT / "src/main/resources").iterdir():
        if resource.is_file():
            shutil.copy2(resource, classes)
    artifact = ROOT / "mods/multimove/multimove.jar"
    artifact.parent.mkdir(exist_ok=True)
    # Fixed ZIP timestamps keep the JAR reproducible for identical inputs.
    with zipfile.ZipFile(artifact, "w", zipfile.ZIP_DEFLATED) as archive:
        manifest = ("Manifest-Version: 1.0\r\nImplementation-Title: MultiMove\r\n"
                    "Implementation-Version: " + VERSION + "\r\n\r\n")
        files = {"META-INF/MANIFEST.MF": manifest.encode("utf-8")}
        files.update({p.relative_to(classes).as_posix(): p.read_bytes()
                      for p in classes.rglob("*") if p.is_file()})
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)
    print("Built " + str(artifact), flush=True)

    if options.test or options.api:
        test_classes = build / "test-classes"
        if test_classes.exists():
            shutil.rmtree(test_classes)
        compile_java(java, deps["ecj-3.26.0.jar"],
                     [str(p) for p in (ROOT / "src/test/java").rglob("*.java")],
                     test_classes, [classes] + compile_deps)
        runtime_cp = os.pathsep.join(map(str, [classes, test_classes] + compile_deps))
        entry = [java, "-Xverify:all", "-cp", runtime_cp,
                 "mod.wurmunlimited.multimove.TestHarness"]
        if options.test:
            run(entry + ["--behavior"] + ([options.quantity_packet.resolve()] if options.quantity_packet else []))
        if options.api:
            api_paths = [str(p.resolve()) for p in options.api]
            run(entry + ["--api"] + api_paths)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print("Build failed: " + str(error), file=sys.stderr)
        sys.exit(1)
