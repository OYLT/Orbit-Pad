"""Verify final release archives, regenerate their hashes, and test the extracted Windows app."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RELEASES = ROOT / 'releases'
manifest = ET.parse(ROOT / 'android/app/src/main/AndroidManifest.xml').getroot()
version = manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']
apk = RELEASES / f'OrbitPad-Android-v{version}.apk'
archive = RELEASES / 'OrbitPad-Receiver-Windows-x64-v0.5.0.zip'
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    assert {'AndroidManifest.xml', 'classes.dex', 'resources.arsc', 'assets/ZXING-LICENSE.txt'}.issubset(z.namelist())
    for abi in ['arm64-v8a', 'x86_64']:
        info = z.getinfo(f'lib/{abi}/liborbitpad.so')
        assert info.compress_type == zipfile.ZIP_STORED
        assert z.read(info).startswith(b'\x7fELF')

destination = ROOT / 'build' / 'release-verification'
destination.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    for name in z.namelist():
        assert (destination / name).resolve().is_relative_to(destination.resolve())
        assert '.jks' not in name and 'android-signing.json' not in name
    z.extractall(destination)
folder = destination / 'OrbitPad-Receiver-Windows-x64'
required = ['OrbitPadReceiver.exe', 'runtime/node.exe', 'runtime/NODE-LICENSE.txt',
            'runtime/platform-tools/adb.exe', 'runtime/platform-tools/AdbWinApi.dll',
            'runtime/platform-tools/AdbWinUsbApi.dll', 'runtime/platform-tools/NOTICE.txt', 'app/server/index.js',
            'app/public/index.html', 'app/native/keyboard.ps1', 'app/node_modules/ws/package.json', 'README-START.txt']
for name in required:
    assert (folder / name).is_file(), name
report = RELEASES / 'windows-extracted-self-test.json'
subprocess.run([str(folder / 'OrbitPadReceiver.exe'), '--self-test', str(report)], check=True, timeout=45,
               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
results = json.loads(report.read_text(encoding='utf-8-sig'))
assert results['ok'] and results['ready'] and results['stopped']
lines = [f'{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.name}' for file in [apk, archive]]
(RELEASES / 'SHA256SUMS.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(json.dumps(results, ensure_ascii=False))
print('\n'.join(lines))
