#!/usr/bin/env bash
# Cobblemon OneBlock test server (test server) - Linux/macOS start script.
# Downloads Fabric API and Cobblemon on first start, verifies SHA-256, asks for the EULA, starts the server.
set -euo pipefail
cd "$(dirname "$0")"

fail() { echo; echo "FEHLER: $1" >&2; exit 1; }

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

fetch() { # name url path sha
  local name=$1 url=$2 path=$3 sha=$4
  if [ -f "$path" ] && [ "$(sha256 "$path")" = "$sha" ]; then return; fi
  echo "Lade $name ..."
  curl -fL --retry 3 -o "$path.part" "$url" || fail "Download von $name fehlgeschlagen."
  local got; got=$(sha256 "$path.part")
  if [ "$got" != "$sha" ]; then rm -f "$path.part"; fail "Pruefsumme von $name stimmt nicht (erhalten $got)."; fi
  mv "$path.part" "$path"
  echo "  ok, Pruefsumme stimmt."
}

command -v java >/dev/null || fail "Java wurde nicht gefunden. Bitte Java 21 installieren (https://adoptium.net)."
version_line=$(java -version 2>&1 | grep -m1 'version "' || true)
major=$(echo "$version_line" | sed -E 's/.*"([0-9]+).*/\1/')
[ "$major" -ge 21 ] 2>/dev/null || fail "Java 21 oder neuer benoetigt, gefunden: $version_line"
echo "Java gefunden: $version_line"

mkdir -p mods
fetch "Fabric API 0.116.17+1.21.1" \
  "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.17+1.21.1/fabric-api-0.116.17+1.21.1.jar" \
  "mods/fabric-api-0.116.17+1.21.1.jar" "79ac44b40780acbd884b34c50be1e39af682847e5f5cb3b1fddeeaa768dce800"
fetch "Cobblemon 1.8.2+1.21.1" \
  "https://cdn.modrinth.com/data/MdwFAVRL/versions/GJ2a1b8O/Cobblemon-fabric-1.8.2%2B1.21.1.jar" \
  "mods/Cobblemon-fabric-1.8.2+1.21.1.jar" "cae8ac328b61354fbe415208b8c6ffc12e7155094d0a87082e85e7914fb380c2"

if ! grep -qs '^eula=true' eula.txt; then
  echo
  echo "Fuer einen Minecraft-Server musst du die Minecraft-EULA akzeptieren: https://aka.ms/MinecraftEULA"
  read -r -p "Akzeptierst du die EULA? (ja/nein) " answer
  case "$answer" in j|ja|y|yes) echo "eula=true" > eula.txt ;; *) fail "EULA nicht akzeptiert." ;; esac
fi

echo
echo 'Starte Server. Zum Beenden "stop" eingeben.'
exec java -Xmx4G -jar fabric-server-launch.jar nogui
