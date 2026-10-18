"""Verify final release archives, regenerate their hashes, and test the extracted Windows app."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RELEASES = ROOT / 'releases'
apk = RELEASES / 'OrbitPad-Android-v0.3.0.apk'
archive = RELEASES / 'OrbitPad-Receiver-Windows-x64-v0.3.0.zip'
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    assert {'AndroidManifest.xml', 'classes.dex', 'resources.arsc'}.issubset(z.namelist())

destination = ROOT / 'build' / 'release-verification'
destination.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    for name in z.namelist():
        assert (destination / name).resolve().is_relative_to(destination.resolve())
        assert '.jks' not in name and 'android-signing.json' not in name
    z.extractall(destination)
folder = destination / 'OrbitPad-Receiver-Windows-x64'
required = ['OrbitPadReceiver.exe', 'runtime/node.exe', 'runtime/NODE-LICENSE.txt', 'app/server/index.js',
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
