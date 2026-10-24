#!/usr/bin/env python3
"""Package the built mod, documentation, and original source into one ZIP."""
from pathlib import Path
import hashlib
import zipfile

root = Path(__file__).resolve().parent
version = (root / "VERSION").read_text(encoding="utf-8").strip()
jar = root / "mods/multimove/multimove.jar"
client_jar = root / "client/mods/multimoveshift/multimoveshift.jar"
for built in [jar, client_jar]:
    if not built.is_file():
        raise SystemExit("Build and test the server and optional client companion first")
    with zipfile.ZipFile(built) as archive:
        if ("Implementation-Version: " + version + "\r\n").encode() not in archive.read("META-INF/MANIFEST.MF"):
            raise SystemExit("Rebuild the outdated JAR: " + str(built))

output = root.parent / ("multimove-" + version + ".zip")
files = {
    "README.md": root / "README.md",
    "LICENSE": root / "LICENSE",
    "mods/multimove.properties": root / "mods/multimove.properties",
    "mods/multimove/multimove.jar": jar,
    "optional-client/README.md": root / "client/README.md",
    "optional-client/mods/multimoveshift.properties": root / "client/mods/multimoveshift.properties",
    "optional-client/mods/multimoveshift/multimoveshift.jar": client_jar,
}
for name in ["build.py", "package.py", "README.md", "LICENSE", "VERSION", ".gitignore",
             "mods/multimove.properties"]:
    files["source/" + name] = root / name
for path in (root / "src").rglob("*"):
    if path.is_file():
        files["source/" + path.relative_to(root).as_posix()] = path
for name in ["build.py", "README.md", "mods/multimoveshift.properties"]:
    files["source/client/" + name] = root / "client" / name
for path in (root / "client/src").rglob("*"):
    if path.is_file():
        files["source/" + path.relative_to(root).as_posix()] = path

with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
    for name, path in sorted(files.items()):
        entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_DEFLATED
        entry.external_attr = 0o644 << 16
        archive.writestr(entry, path.read_bytes())
with zipfile.ZipFile(output) as archive:
    if archive.testzip() is not None:
        raise SystemExit("ZIP validation failed")
print(str(output))
print("SHA-256: " + hashlib.sha256(output.read_bytes()).hexdigest())
