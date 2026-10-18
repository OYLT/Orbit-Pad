"""Compile and sign a standalone APK using official Android SDK tools, without Gradle."""
from pathlib import Path
import hashlib
import json
import os
import secrets
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.build-tools'
SDK = TOOLS / 'android-sdk'
PLATFORM = SDK / 'platforms/android-35/android.jar'
BUILD_TOOLS = SDK / 'build-tools/35.0.0'
TEST_BUILD = '--instrumentation' in sys.argv
BUILD = ROOT / ('build/android-test' if TEST_BUILD else 'build/android')
OUT = BUILD if TEST_BUILD else ROOT / 'releases'
JAVA_CANDIDATES = [os.getenv('ORBIT_JAVA_HOME'), os.getenv('JAVA_HOME'),
    r'C:\Program Files\STMicroelectronics\STM32Cube\STM32CubeMX\jre',
    r'C:\Program Files\Android\Android Studio\jbr']
JAVA = next((Path(p) for p in JAVA_CANDIDATES if p and (Path(p) / 'bin/javac.exe').exists()), None)
if JAVA is None:
    raise RuntimeError('Set JAVA_HOME to a JDK 17 or newer, then run this script again.')
for directory in [BUILD / 'generated', BUILD / 'classes', BUILD / 'dex', OUT]:
    directory.mkdir(parents=True, exist_ok=True)

def run(args, **kwargs):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True, **kwargs)

source = ROOT / ('android/device-test/src/main' if TEST_BUILD else 'android/app/src/main')
run([BUILD_TOOLS / 'aapt2.exe', 'compile', '--dir', source / 'res', '-o', BUILD / 'resources.zip'])
run([BUILD_TOOLS / 'aapt2.exe', 'link', '-o', BUILD / 'base.apk', '-I', PLATFORM,
    '--manifest', source / 'AndroidManifest.xml', '--java', BUILD / 'generated', '--auto-add-overlay', BUILD / 'resources.zip'])
sources = list((source / 'java').rglob('*.java')) + list((BUILD / 'generated').rglob('*.java'))
run([JAVA / 'bin/javac.exe', '-encoding', 'UTF-8', '--release', '8', '-classpath', PLATFORM,
    '-d', BUILD / 'classes', *sources])
with zipfile.ZipFile(BUILD / 'classes.jar', 'w') as z:
    for file in (BUILD / 'classes').rglob('*.class'):
        z.write(file, file.relative_to(BUILD / 'classes').as_posix())
run([JAVA / 'bin/java.exe', '-cp', BUILD_TOOLS / 'lib/d8.jar', 'com.android.tools.r8.D8',
    '--release', '--min-api', '26', '--lib', PLATFORM, '--output', BUILD / 'dex', BUILD / 'classes.jar'])
shutil.copy2(BUILD / 'base.apk', BUILD / 'unsigned.apk')
with zipfile.ZipFile(BUILD / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    for file in (BUILD / 'dex').glob('*.dex'):
        z.write(file, file.name)
run([BUILD_TOOLS / 'zipalign.exe', '-f', '-p', '4', BUILD / 'unsigned.apk', BUILD / 'aligned.apk'])

credentials = TOOLS / 'android-signing.json'
keystore = TOOLS / 'orbitpad-release.jks'
if not credentials.exists():
    credentials.write_text(json.dumps({'password': secrets.token_urlsafe(32), 'alias': 'orbitpad'}), encoding='utf-8')
    # Limit access to the local builder on Windows. The directory is excluded from Git and releases.
    if os.name == 'nt':
        subprocess.run(['icacls', str(credentials), '/inheritance:r', '/grant:r', f'{os.environ["USERNAME"]}:(F)'], capture_output=True, check=True)
config = json.loads(credentials.read_text(encoding='utf-8'))
env = dict(os.environ, ORBIT_STORE_PASSWORD=config['password'])
if not keystore.exists():
    run([JAVA / 'bin/keytool.exe', '-genkeypair', '-keystore', keystore, '-storetype', 'JKS', '-alias', config['alias'],
        '-storepass:env', 'ORBIT_STORE_PASSWORD', '-keypass:env', 'ORBIT_STORE_PASSWORD',
        '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Orbit Pad Local Release, O=Orbit Pad, C=SG'], env=env)
    if os.name == 'nt':
        subprocess.run(['icacls', str(keystore), '/inheritance:r', '/grant:r', f'{os.environ["USERNAME"]}:(F)'], capture_output=True, check=True)
apk = OUT / ('OrbitPad-device-tests.apk' if TEST_BUILD else 'OrbitPad-Android-v0.3.0.apk')
signer = [JAVA / 'bin/java.exe', '-jar', BUILD_TOOLS / 'lib/apksigner.jar']
run([*signer, 'sign', '--ks', keystore, '--ks-key-alias', config['alias'], '--ks-pass', 'env:ORBIT_STORE_PASSWORD',
    '--key-pass', 'env:ORBIT_STORE_PASSWORD', '--out', apk, BUILD / 'aligned.apk'], env=env)
verification = subprocess.run([str(x) for x in [*signer, 'verify', '--verbose', '--print-certs', apk]], cwd=ROOT, capture_output=True, text=True, check=True)
(OUT / 'android-signature-verification.txt').write_text(verification.stdout, encoding='utf-8')
run([BUILD_TOOLS / 'zipalign.exe', '-c', '-v', '4', apk], stdout=subprocess.DEVNULL)
metadata = subprocess.run([str(BUILD_TOOLS / 'aapt2.exe'), 'dump', 'badging', str(apk)], capture_output=True, text=True, check=True)
(OUT / 'android-package-info.txt').write_text(metadata.stdout, encoding='utf-8')
print(verification.stdout)
print(f'APK ready: {apk} ({apk.stat().st_size:,} bytes)')
print(f'SHA256: {hashlib.sha256(apk.read_bytes()).hexdigest()}')
