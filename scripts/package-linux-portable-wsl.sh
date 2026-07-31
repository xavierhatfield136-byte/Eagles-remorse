#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

version="$(tr -d '\r\n' < VERSION)"
jdk_root="build/linux-tools/jdk"
jdk_archive="build/linux-tools/temurin21-linux-x64-jdk.tar.gz"
jdk_extract="build/linux-tools/jdk-extract"

mkdir -p build/linux-tools
if [ ! -x "$jdk_root/bin/jpackage" ]; then
  curl -L --fail --retry 3 \
    -o "$jdk_archive" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  rm -rf "$jdk_extract" "$jdk_root"
  mkdir -p "$jdk_extract"
  tar -xzf "$jdk_archive" -C "$jdk_extract"
  first_dir="$(find "$jdk_extract" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
  mv "$first_dir" "$jdk_root"
fi

slim_dir="build/release-slim-$version"
slim_jar="$slim_dir/EaglesRemorse-$version.jar"
mkdir -p "$slim_dir"
"$jdk_root/bin/jar" --create --file "$slim_jar" -C build/classes/java/main .
cp VERSION "$slim_dir/VERSION"
(cd "$slim_dir" && "../linux-tools/jdk/bin/jar" --update --file "EaglesRemorse-$version.jar" VERSION)

input_dir="build/jpackage/linux-slim-input-$version"
dest_dir="build/package/linux"
app_root="$dest_dir/EaglesRemorse"
mkdir -p "$input_dir" "$dest_dir"
cp "$slim_jar" "$input_dir/"
if [ -d "$app_root" ]; then
  mv "$app_root" "$dest_dir/EaglesRemorse.previous.$(date +%s)"
fi

"$jdk_root/bin/jpackage" \
  --type app-image \
  --input "$input_dir" \
  --dest "$dest_dir" \
  --name EaglesRemorse \
  --main-jar "EaglesRemorse-$version.jar" \
  --main-class Main \
  --app-version 1.0.1008 \
  --vendor xhatf \
  --description "Eagles Remorse" \
  --java-options "-Dfile.encoding=UTF-8"

cat > "$app_root/EaglesRemorse" <<'RUNNER'
#!/usr/bin/env bash
set -euo pipefail
dir="$(cd "$(dirname "$0")" && pwd)"
exec "$dir/bin/EaglesRemorse" "$@"
RUNNER
chmod +x "$app_root/EaglesRemorse"

asset_root="$app_root/assets"
mkdir -p "$asset_root"
for dir in \
  audio crew_portraits environment_overhaul_dropzone hud_panels projectile_skins \
  ship_damage_patches ship_parts ship_skins ship_wrecks station_modules \
  turret_skins ui ui_theme voice
do
  mkdir -p "$asset_root/$dir"
  cp -a "assets/$dir/." "$asset_root/$dir/"
done

cp VERSION "$app_root/VERSION"
cp LICENSE.md "$app_root/LICENSE.txt"
cat > "$app_root/README_INSTALL.txt" <<README
Eagles Remorse $version - Portable Linux Install

1. Extract the ZIP into a normal folder.
2. Run: ./EaglesRemorse
3. If your unzip tool drops executable bits, run: chmod +x EaglesRemorse bin/EaglesRemorse
4. If visuals or audio look missing, run: bash verify-install.sh

This full package includes a bundled Linux Java runtime and the external assets
folder used by ship hulls, HUD panels, UI buttons, audio, voice, environments,
turrets, projectiles, wrecks, and damage patches.
README

cat > "$app_root/verify-install.sh" <<'VERIFY'
#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
manifest="$root/package_content_manifest.json"
python3 - "$root" "$manifest" <<'PY'
import hashlib, json, pathlib, sys
root = pathlib.Path(sys.argv[1])
manifest = pathlib.Path(sys.argv[2])
data = json.loads(manifest.read_text())
errors = []
checked = 0
for item in data["files"]:
    path = root / item["path"]
    if not path.is_file():
        errors.append(f"missing: {item['path']}")
        continue
    if path.stat().st_size != int(item["size"]):
        errors.append(f"size mismatch: {item['path']}")
        continue
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != item["sha256"].lower():
        errors.append(f"sha256 mismatch: {item['path']}")
        continue
    checked += 1
if errors:
    print(f"INSTALL INCOMPLETE: {len(errors)} problem(s).")
    print("\n".join(errors[:50]))
    sys.exit(1)
print(f"INSTALL VERIFIED: {checked} / {len(data['files'])} files match.")
PY
VERIFY
chmod +x "$app_root/verify-install.sh"

python3 - "$app_root" "$version" <<'PY'
import hashlib, json, os, pathlib, stat, sys, zipfile

root = pathlib.Path(sys.argv[1])
version = sys.argv[2]
package_dir = root.parent
zip_path = package_dir / f"EaglesRemorse-{version}-linux-x64-full.zip"

def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def rel(path: pathlib.Path) -> str:
    return path.relative_to(root).as_posix()

def all_files():
    return sorted((p for p in root.rglob("*") if p.is_file()), key=lambda p: rel(p))

def write_manifest(path: pathlib.Path, platform: str):
    files = [
        {
            "path": rel(p),
            "size": p.stat().st_size,
            "sha256": sha256(p),
            "required": True,
        }
        for p in all_files()
        if p.name != "package_content_manifest.json"
    ]
    data = {
        "schema": "package-content-manifest-v2",
        "version": version,
        "platform": platform,
        "fileCount": len(files),
        "totalBytes": sum(item["size"] for item in files),
        "files": files,
    }
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

asset_files = [
    {
        "path": p.relative_to(root / "assets").as_posix(),
        "size": p.stat().st_size,
        "sha256": sha256(p),
        "required": True,
    }
    for p in sorted((root / "assets").rglob("*"))
    if p.is_file()
]
(root / "external_asset_manifest.json").write_text(json.dumps({
    "schema": "external-asset-manifest-v1",
    "version": version,
    "assetCount": len(asset_files),
    "totalBytes": sum(item["size"] for item in asset_files),
    "files": asset_files,
}, indent=2) + "\n", encoding="utf-8")

write_manifest(root / "package_content_manifest.json", "linux-x64")
write_manifest(root / "package_content_manifest.json", "linux-x64")

if zip_path.exists():
    zip_path.unlink()
with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
    for p in all_files():
        name = rel(p)
        info = zipfile.ZipInfo.from_file(p, arcname=name)
        mode = p.stat().st_mode
        if name == "EaglesRemorse" or name.endswith(".sh") or "/bin/" in name:
            mode |= stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH
        info.external_attr = (mode & 0xFFFF) << 16
        with p.open("rb") as f:
            zf.writestr(info, f.read(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)

manifest = json.loads((root / "package_content_manifest.json").read_text())
with zipfile.ZipFile(zip_path, "r") as zf:
    entries = {name: zf.getinfo(name) for name in zf.namelist() if not name.endswith("/")}
    errors = []
    for item in manifest["files"]:
        entry = entries.get(item["path"])
        if entry is None:
            errors.append(f"missing in zip: {item['path']}")
            continue
        if entry.file_size != item["size"]:
            errors.append(f"size mismatch in zip: {item['path']}")
            continue
        if hashlib.sha256(zf.read(entry)).hexdigest() != item["sha256"]:
            errors.append(f"sha mismatch in zip: {item['path']}")
            continue
    if errors:
        raise SystemExit("\n".join(errors[:50]))

zip_hash = sha256(zip_path)
(package_dir / "SHA256SUMS-linux.txt").write_text(f"{zip_hash}  {zip_path.name}\n", encoding="ascii")
print(f"PACKAGE: {zip_path}")
print(f"SHA256:  {zip_hash}")
print(f"BYTES:   {zip_path.stat().st_size}")
PY
