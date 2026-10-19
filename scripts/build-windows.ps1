$ErrorActionPreference = 'Stop'
$orbitRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $orbitRoot
python scripts/setup-direct-connections.py
if ($LASTEXITCODE -ne 0) { throw 'USB component setup failed.' }
$orbitOut = Join-Path $orbitRoot 'releases\OrbitPad-Receiver-Windows-x64'
New-Item -ItemType Directory -Force -Path $orbitOut,(Join-Path $orbitOut 'app'),(Join-Path $orbitOut 'runtime'),(Join-Path $orbitRoot 'build\windows') | Out-Null
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $compiler)) { throw 'The Windows .NET Framework C# compiler is required.' }

Add-Type -AssemblyName System.Drawing
$bitmap = New-Object System.Drawing.Bitmap(64,64)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::FromArgb(208,188,255))
$pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(56,30,114),4)
$pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
$points = [System.Drawing.Point[]]@((New-Object System.Drawing.Point(18,22)),(New-Object System.Drawing.Point(46,22)),(New-Object System.Drawing.Point(54,45)),(New-Object System.Drawing.Point(47,47)),(New-Object System.Drawing.Point(39,40)),(New-Object System.Drawing.Point(25,40)),(New-Object System.Drawing.Point(17,47)),(New-Object System.Drawing.Point(10,45)))
$graphics.DrawPolygon($pen,$points)
$graphics.DrawLine($pen,22,27,22,38)
$graphics.DrawLine($pen,17,32,27,32)
$graphics.DrawEllipse($pen,39,28,2,2)
$graphics.DrawEllipse($pen,45,34,2,2)
$iconPath = Join-Path $orbitRoot 'build\windows\orbit.ico'
$icon = [System.Drawing.Icon]::FromHandle($bitmap.GetHicon())
$iconStream = [System.IO.File]::Create($iconPath)
$icon.Save($iconStream)
$iconStream.Dispose()
$graphics.Dispose()
$bitmap.Dispose()
$pen.Dispose()

$exePath = Join-Path $orbitOut 'OrbitPadReceiver.exe'
& $compiler /nologo /target:winexe /platform:x64 /optimize+ /codepage:65001 "/out:$exePath" "/win32icon:$iconPath" "/win32manifest:$orbitRoot\desktop\app.manifest" /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.Net.Http.dll /reference:System.Web.Extensions.dll "$orbitRoot\desktop\OrbitPadReceiver.cs" "$orbitRoot\desktop\DirectConnections.cs"
if ($LASTEXITCODE -ne 0) { throw 'Windows receiver compilation failed.' }
Copy-Item -LiteralPath (Get-Command node.exe).Source -Destination (Join-Path $orbitOut 'runtime\node.exe') -Force
Copy-Item -LiteralPath (Join-Path $orbitRoot '.build-tools\android-sdk\platform-tools') -Destination (Join-Path $orbitOut 'runtime') -Recurse -Force
$nodeLicense = Join-Path $orbitRoot '.build-tools\NODE-LICENSE.txt'
if (-not (Test-Path -LiteralPath $nodeLicense)) {
    $nodeVersion = (& node -p 'process.version').Trim()
    Invoke-WebRequest -Uri "https://raw.githubusercontent.com/nodejs/node/$nodeVersion/LICENSE" -OutFile $nodeLicense -UseBasicParsing
}
Copy-Item -LiteralPath $nodeLicense -Destination (Join-Path $orbitOut 'runtime\NODE-LICENSE.txt') -Force
foreach ($folder in @('server','native','public','node_modules')) {
    Copy-Item -LiteralPath (Join-Path $orbitRoot $folder) -Destination (Join-Path $orbitOut 'app') -Recurse -Force
}
Copy-Item -LiteralPath (Join-Path $orbitRoot 'package.json') -Destination (Join-Path $orbitOut 'app\package.json') -Force
Copy-Item -LiteralPath (Join-Path $orbitRoot 'docs\README-START.txt') -Destination (Join-Path $orbitOut 'README-START.txt') -Force
@'
<?xml version="1.0" encoding="utf-8"?><configuration><startup><supportedRuntime version="v4.0" sku=".NETFramework,Version=v4.7.2"/></startup><runtime><AppContextSwitchOverrides value="Switch.System.Windows.Forms.DoNotSupportDpiChanges=false"/></runtime></configuration>
'@ | Set-Content -LiteralPath (Join-Path $orbitOut 'OrbitPadReceiver.exe.config') -Encoding UTF8

$reportPath = Join-Path $orbitRoot 'releases\windows-self-test.json'
$selfTest = Start-Process -FilePath $exePath -ArgumentList ('--self-test "' + $reportPath + '"') -WindowStyle Hidden -PassThru -Wait
if ($selfTest.ExitCode -ne 0) {
    if (Test-Path -LiteralPath $reportPath) { Get-Content -LiteralPath $reportPath }
    throw 'Packaged Windows receiver self-test failed.'
}
Get-Content -LiteralPath $reportPath
$archive = Join-Path $orbitRoot 'releases\OrbitPad-Receiver-Windows-x64-v0.5.0.zip'
Compress-Archive -LiteralPath $orbitOut -DestinationPath $archive -CompressionLevel Optimal -Force
Write-Output "Windows receiver ready: $archive"
