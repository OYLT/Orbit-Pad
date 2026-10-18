param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if ($JavaHome) { $env:ORBIT_JAVA_HOME = $JavaHome }
python scripts/setup-android.py
if ($LASTEXITCODE -ne 0) { throw 'Android SDK setup failed' }
python scripts/build-android.py
if ($LASTEXITCODE -ne 0) { throw 'Android APK build failed' }
