# Cobblemon OneBlock test server (test server) - Windows start script.
# Downloads Fabric API and Cobblemon on first start, verifies SHA-256, asks for the EULA, starts the server.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$downloads = @(
    @{
        Name = 'Fabric API 0.116.17+1.21.1'
        Url  = 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.17+1.21.1/fabric-api-0.116.17+1.21.1.jar'
        Path = 'mods\fabric-api-0.116.17+1.21.1.jar'
        Sha  = '79ac44b40780acbd884b34c50be1e39af682847e5f5cb3b1fddeeaa768dce800'
    },
    @{
        Name = 'Cobblemon 1.8.2+1.21.1'
        Url  = 'https://cdn.modrinth.com/data/MdwFAVRL/versions/GJ2a1b8O/Cobblemon-fabric-1.8.2%2B1.21.1.jar'
        Path = 'mods\Cobblemon-fabric-1.8.2+1.21.1.jar'
        Sha  = 'cae8ac328b61354fbe415208b8c6ffc12e7155094d0a87082e85e7914fb380c2'
    }
)

function Fail($message) {
    Write-Host ''
    Write-Host "FEHLER: $message" -ForegroundColor Red
    Read-Host 'Enter druecken zum Schliessen'
    exit 1
}

# Java 21 or newer
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Fail 'Java wurde nicht gefunden. Bitte Java 21 installieren (z. B. https://adoptium.net, "Temurin 21") und neu starten.'
}
# cmd /c keeps java's stderr away from PowerShell's error handling
$javaVersion = [string](cmd /c "java -version 2>&1" | Where-Object { $_ -match 'version "' } | Select-Object -First 1)
if ($javaVersion -notmatch '"(\d+)') { Fail "Java-Version nicht erkennbar: $javaVersion" }
if ([int]$Matches[1] -lt 21) { Fail "Java $($Matches[1]) gefunden, benoetigt wird Java 21 oder neuer (https://adoptium.net)." }
Write-Host "Java gefunden: $javaVersion"

# Mods
foreach ($d in $downloads) {
    $ok = (Test-Path $d.Path) -and ((Get-FileHash $d.Path -Algorithm SHA256).Hash.ToLower() -eq $d.Sha)
    if (-not $ok) {
        Write-Host "Lade $($d.Name) ..."
        $tmp = "$($d.Path).part"
        Invoke-WebRequest -Uri $d.Url -OutFile $tmp -UseBasicParsing
        $hash = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower()
        if ($hash -ne $d.Sha) {
            Remove-Item $tmp -Force
            Fail "Pruefsumme von $($d.Name) stimmt nicht (erhalten $hash). Download abgebrochen."
        }
        Move-Item $tmp $d.Path -Force
        Write-Host "  ok, Pruefsumme stimmt."
    }
}

# EULA
if (-not ((Test-Path 'eula.txt') -and (Select-String -Path 'eula.txt' -Pattern '^eula=true' -Quiet))) {
    Write-Host ''
    Write-Host 'Fuer einen Minecraft-Server musst du die Minecraft-EULA akzeptieren:'
    Write-Host '  https://aka.ms/MinecraftEULA'
    $answer = Read-Host 'Akzeptierst du die EULA? (ja/nein)'
    if ($answer -notmatch '^(j|ja|y|yes)$') { Fail 'EULA nicht akzeptiert, Server wird nicht gestartet.' }
    Set-Content -Path 'eula.txt' -Value 'eula=true' -Encoding ascii
}

Write-Host ''
Write-Host 'Starte Server. Das Server-Fenster oeffnet sich gleich (erster Start dauert etwas).'
Write-Host 'Zum Beenden im Server-Fenster "stop" eingeben.'
& java -Xmx4G -jar fabric-server-launch.jar
