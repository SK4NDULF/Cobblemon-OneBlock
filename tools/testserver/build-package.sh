#!/usr/bin/env bash
# Builds the mod and assembles a test-server zip for the owner: build/testserver/OneBlock-Testserver.zip
# The zip contains the mod jar, the Fabric server launcher and start scripts. Fabric API and Cobblemon are
# downloaded (with SHA-256 check) by the start scripts on first start, so nothing third-party is redistributed.
set -euo pipefail
cd "$(dirname "$0")/../.."
./gradlew :oneblock-core:build --console=plain -q
out=build/testserver/OneBlock-Testserver
rm -rf build/testserver
mkdir -p "$out/mods" "$out/scripts"
cp tools/testserver/START-Windows.bat tools/testserver/start-linux-mac.sh tools/testserver/server.properties tools/testserver/ANLEITUNG.txt "$out/"
cp tools/testserver/scripts/start.ps1 "$out/scripts/"
cp oneblock-core/build/libs/cobblemon-oneblock-*-dev.jar "$out/mods/" 2>/dev/null || cp oneblock-core/build/libs/cobblemon-oneblock-[0-9]*[0-9].jar "$out/mods/"
rm -f "$out"/mods/*-sources.jar
curl -fsSL -o "$out/fabric-server-launch.jar" "https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.19.5/1.1.2/server/jar"
echo "809d99b16dd4d4b57384d128724ab0d6560f859aaba989dc6750d8437a447ed0  $out/fabric-server-launch.jar" | sha256sum -c -
(cd build/testserver && python3 - <<'PY'
import os, zipfile
src = 'OneBlock-Testserver'
with zipfile.ZipFile(src + '.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for dp, dn, fn in os.walk(src):
        for f in sorted(fn):
            p = os.path.join(dp, f)
            zi = zipfile.ZipInfo.from_file(p)
            zi.compress_type = zipfile.ZIP_DEFLATED
            if f.endswith('.sh'):
                zi.external_attr = 0o100755 << 16
            z.writestr(zi, open(p, 'rb').read())
PY
)
echo "build/testserver/OneBlock-Testserver.zip"
