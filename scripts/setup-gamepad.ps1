param([switch]$IncludeDriver)
$ErrorActionPreference = 'Stop'
$orbitRoot = Split-Path -Parent $PSScriptRoot
$orbitCache = Join-Path $orbitRoot '.build-tools\vigem'
$orbitBin = Join-Path $orbitRoot 'native\bin'
New-Item -ItemType Directory -Force -Path $orbitCache,$orbitBin | Out-Null

function Get-OrbitHash($Path) {
    $orbitSha = [Security.Cryptography.SHA256]::Create()
    $orbitStream = [IO.File]::OpenRead($Path)
    try { return [BitConverter]::ToString($orbitSha.ComputeHash($orbitStream)).Replace('-', '') }
    finally { $orbitStream.Dispose(); $orbitSha.Dispose() }
}
function Get-OrbitDependency($Url, $Path, $Hash) {
    if (-not (Test-Path -LiteralPath $Path) -or (Get-OrbitHash $Path) -ne $Hash) {
        Invoke-WebRequest -Uri $Url -OutFile $Path -UseBasicParsing -TimeoutSec 180
    }
    if ((Get-OrbitHash $Path) -ne $Hash) { throw "Dependency checksum mismatch: $Path" }
}

# The upstream managed package embeds the official native client DLLs.
$orbitPackage = Join-Path $orbitCache 'client.nupkg'
Get-OrbitDependency 'https://api.nuget.org/v3-flatcontainer/nefarius.vigem.client/1.21.256/nefarius.vigem.client.1.21.256.nupkg' $orbitPackage 'FF460031B33E0882CD1166FE2D072D0FE97578480C61ED85DCCFA6C930FDB55E'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$orbitZip = [IO.Compression.ZipFile]::OpenRead($orbitPackage)
try {
    $orbitEntry = $orbitZip.GetEntry('lib/netstandard2.0/Nefarius.ViGEm.Client.dll')
    $orbitManaged = Join-Path $orbitCache 'Nefarius.ViGEm.Client.dll'
    [IO.Compression.ZipFileExtensions]::ExtractToFile($orbitEntry, $orbitManaged, $true)
} finally { $orbitZip.Dispose() }
$orbitAssembly = [Reflection.Assembly]::LoadFile($orbitManaged)
$orbitResource = $orbitAssembly.GetManifestResourceStream('costura64.vigemclient.dll')
try {
    $orbitOutput = [IO.File]::Create((Join-Path $orbitBin 'ViGEmClient.dll'))
    try { $orbitResource.CopyTo($orbitOutput) } finally { $orbitOutput.Dispose() }
} finally { $orbitResource.Dispose() }
$orbitCompiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $orbitCompiler /nologo /target:exe /platform:x64 /optimize+ /codepage:65001 "/out:$orbitBin\OrbitPadGamepad.exe" "$orbitRoot\native\GamepadBridge.cs"
if ($LASTEXITCODE -ne 0) { throw 'Native gamepad compilation failed.' }
Copy-Item -LiteralPath (Join-Path $orbitRoot 'native\ViGEm-LICENSE.txt') -Destination $orbitBin -Force
if ($IncludeDriver) {
    $orbitInstaller = Join-Path $orbitCache 'ViGEmBus_1.22.0_x64_x86_arm64.exe'
    Get-OrbitDependency 'https://github.com/nefarius/ViGEmBus/releases/download/v1.22.0/ViGEmBus_1.22.0_x64_x86_arm64.exe' $orbitInstaller '89220A7865076B342892F98865F3499FB7C4CFD673159E89D352C360FD014C6A'
    Copy-Item -LiteralPath $orbitInstaller -Destination $orbitBin -Force
}
Write-Output 'Native Xbox 360 gamepad component ready. Install ViGEmBus once before using npm start.'
