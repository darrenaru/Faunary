#!/usr/bin/env python3
"""Build and publish a Faunary release for the in-app updater.

    python tools/release.py --notes "Perbaikan peta dan rute"
    python tools/release.py --notes "..." --version-name 0.4.0
    python tools/release.py --notes "..." --mandatory     # older versions must update first
    python tools/release.py --notes-file notes.txt        # one change per line, "- " for bullets

Every release is also added to the manifest's changelog (newest first), so the app can show all
changes since the version a user has installed before they update.

Steps: bump version.properties -> assembleRelease (one APK per ABI) -> for each of the last few
published versions, build a bsdiff patch (jbsdiff, the same library the app uses to apply it) ->
upload APKs + patches -> upload latest.json, which installed apps poll.

Needs SUPABASE_URL in local.properties and a service-role key, either in the environment
(FAUNARY_SERVICE_ROLE_KEY) or local.properties (SUPABASE_SERVICE_ROLE_KEY). The service key is only
used here to upload; it is never part of the app.
"""
import argparse
import datetime
import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUCKET = "app-releases"
ABIS = ["arm64-v8a", "armeabi-v7a"]
KEEP_HISTORY = 4          # patches are generated from this many previous versions
MAX_PATCH_RATIO = 0.8     # skip patches that save less than 20%
KEEP_CHANGELOG = 30       # versions kept in the manifest's changelog


def read_props(path: Path) -> dict:
    props = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    return props


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def http(method, url, data=None, headers=None):
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


class Storage:
    def __init__(self, url, key):
        self.base = url.rstrip("/") + "/storage/v1/object"
        self.key = key

    def public_url(self, path):
        return f"{self.base}/public/{BUCKET}/{path}"

    def upload(self, path, data: bytes, content_type, cache="max-age=31536000"):
        status, body = http("POST", f"{self.base}/{BUCKET}/{path}", data, {
            "Authorization": f"Bearer {self.key}", "apikey": self.key,
            "Content-Type": content_type, "x-upsert": "true", "cache-control": cache,
        })
        if status >= 300:
            sys.exit(f"Upload {path} gagal ({status}): {body[:300]!r}")

    def download(self, path, target: Path):
        status, body = http("GET", self.public_url(path))
        if status >= 300:
            return False
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(body)
        return True


def java_exe():
    home = os.environ.get("JAVA_HOME") or r"C:\Program Files\Android\Android Studio\jbr"
    exe = Path(home) / "bin" / ("java.exe" if os.name == "nt" else "java")
    return str(exe) if exe.exists() else "java"


def jbsdiff_classpath():
    cache = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"
    jars = []
    for group, name in [("io.sigpipe", "jbsdiff"), ("org.apache.commons", "commons-compress"),
                        ("commons-io", "commons-io"), ("org.apache.commons", "commons-lang3")]:
        found = sorted((cache / group / name).glob("*/*/*.jar"))
        found = [j for j in found if not j.name.endswith(("-sources.jar", "-javadoc.jar"))]
        if found:
            jars.append(str(found[-1]))
    if len(jars) < 2:
        sys.exit("jbsdiff/commons-compress tidak ditemukan di cache Gradle. Jalankan build sekali dulu.")
    return os.pathsep.join(jars)


def make_patch(old: Path, new: Path, out: Path):
    subprocess.run([java_exe(), "-Xmx2g", "-cp", jbsdiff_classpath(), "io.sigpipe.jbsdiff.ui.CLI",
                    "diff", str(old), str(new), str(out)], check=True)


def main():
    ap = argparse.ArgumentParser()
    notes_arg = ap.add_mutually_exclusive_group(required=True)
    notes_arg.add_argument("--notes", help="Catatan rilis yang tampil di aplikasi (baris baru = poin baru)")
    notes_arg.add_argument("--notes-file", help="File teks berisi catatan rilis, satu perubahan per baris")
    ap.add_argument("--version-name", help="Default: naikkan angka terakhir, mis. 0.2.0 -> 0.2.1")
    ap.add_argument("--no-bump", action="store_true", help="Pakai versi saat ini (mis. mengulang upload)")
    ap.add_argument("--mandatory", action="store_true",
                    help="Pembaruan wajib: versi yang lebih lama diblokir sampai memperbarui")
    args = ap.parse_args()
    notes = (Path(args.notes_file).read_text(encoding="utf-8") if args.notes_file else args.notes).strip()

    local = read_props(ROOT / "local.properties")
    url = local.get("SUPABASE_URL")
    key = os.environ.get("FAUNARY_SERVICE_ROLE_KEY") or local.get("SUPABASE_SERVICE_ROLE_KEY")
    if not url or not key:
        sys.exit("Butuh SUPABASE_URL di local.properties dan kunci service role "
                 "(env FAUNARY_SERVICE_ROLE_KEY atau SUPABASE_SERVICE_ROLE_KEY di local.properties).")
    storage = Storage(url, key)

    version_file = ROOT / "version.properties"
    version = read_props(version_file)
    code = int(version["VERSION_CODE"]) + (0 if args.no_bump else 1)
    if args.version_name:
        name = args.version_name
    elif args.no_bump:
        name = version["VERSION_NAME"]
    else:
        parts = version["VERSION_NAME"].split(".")
        parts[-1] = str(int(parts[-1]) + 1)
        name = ".".join(parts)
    version_file.write_text(
        "# Bumped by tools/release.py on every release. VERSION_CODE must always increase.\n"
        f"VERSION_CODE={code}\nVERSION_NAME={name}\n", encoding="utf-8")
    print(f"==> Membangun versi {name} (code {code})")

    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    env = dict(os.environ, JAVA_HOME=os.environ.get("JAVA_HOME") or r"C:\Program Files\Android\Android Studio\jbr")
    subprocess.run([str(gradlew), ":app:assembleRelease", "--console=plain", "-q"], cwd=ROOT, check=True, env=env)

    status, body = http("GET", storage.public_url("latest.json") + f"?t={code}")
    current = json.loads(body) if status == 200 else None
    if current and current["versionCode"] >= code:
        sys.exit(f"Versi di server ({current['versionCode']}) sudah >= {code}. Naikkan versi.")
    history = []
    if current:
        history = [{"versionCode": current["versionCode"],
                    "abis": {abi: a["apk"] for abi, a in current["abis"].items()}}] + current.get("history", [])
        history = history[:KEEP_HISTORY]

    work = ROOT / "build" / "release-cache"
    # Once a release is mandatory, later optional releases keep that floor.
    min_code = code if args.mandatory else (current or {}).get("minVersionCode", 0)
    changelog = [{"versionCode": code, "versionName": name, "notes": notes,
                  "date": datetime.date.today().isoformat()}]
    changelog += [e for e in (current or {}).get("changelog", []) if e["versionCode"] < code]
    manifest = {"versionCode": code, "versionName": name, "notes": notes, "minVersionCode": min_code,
                "abis": {}, "history": history, "changelog": changelog[:KEEP_CHANGELOG]}
    for abi in ABIS:
        apk = ROOT / "app" / "build" / "outputs" / "apk" / "release" / f"app-{abi}-release.apk"
        if not apk.exists():
            continue
        apk_entry = {"path": f"{code}/{abi}.apk", "size": apk.stat().st_size, "sha256": sha256(apk)}
        print(f"==> {abi}: {apk_entry['size'] / 1e6:.1f} MB")
        storage.upload(apk_entry["path"], apk.read_bytes(), "application/vnd.android.package-archive")

        patches = []
        for prev in history:
            old_entry = prev["abis"].get(abi)
            if not old_entry:
                continue
            old = work / str(prev["versionCode"]) / f"{abi}.apk"
            if not (old.exists() and sha256(old) == old_entry["sha256"]):
                if not storage.download(old_entry["path"], old) or sha256(old) != old_entry["sha256"]:
                    print(f"    lewati patch dari {prev['versionCode']} (APK lama tidak tersedia)")
                    continue
            patch = work / str(code) / f"{abi}-from-{prev['versionCode']}.patch"
            patch.parent.mkdir(parents=True, exist_ok=True)
            make_patch(old, apk, patch)
            size = patch.stat().st_size
            if size > MAX_PATCH_RATIO * apk_entry["size"]:
                print(f"    patch dari {prev['versionCode']} terlalu besar ({size / 1e6:.1f} MB), dilewati")
                continue
            path = f"{code}/{abi}-from-{prev['versionCode']}.patch"
            storage.upload(path, patch.read_bytes(), "application/octet-stream")
            patches.append({"fromSha256": old_entry["sha256"], "path": path, "size": size, "sha256": sha256(patch)})
            print(f"    patch dari {prev['versionCode']}: {size / 1e6:.2f} MB")
        manifest["abis"][abi] = {"apk": apk_entry, "patches": patches}

    if not manifest["abis"]:
        sys.exit("Tidak ada APK release yang ditemukan.")
    storage.upload("latest.json", json.dumps(manifest, indent=2, ensure_ascii=False).encode("utf-8"),
                   "application/json", cache="no-cache, max-age=0")
    kind = "WAJIB" if min_code >= code else "opsional"
    print(f"==> Terbit ({kind}): {name} (code {code}). Aplikasi akan menemukannya saat dibuka / dalam 12 jam.")


if __name__ == "__main__":
    main()
