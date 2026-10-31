"""Run Dock rendering/navigation checks on a connected Android device. No receiver needed."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--suite', choices=['glass', 'motion', 'edge', 'polish', 'layout'], default='glass')
parser.add_argument('--output', default='artifacts/liquid-glass')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = root / args.output
out.mkdir(parents=True, exist_ok=True)
adb = [str(root / '.build-tools/android-sdk/platform-tools/adb.exe'), '-s', args.serial]

def run(*command, **kwargs):
    return subprocess.run([*adb, *command], check=True, **kwargs)

api = int(subprocess.check_output([*adb, 'shell', 'getprop', 'ro.build.version.sdk'], text=True).strip())
if api < 33:
    parser.error('The full shader comparison suite requires Android 13 or newer.')
run('install', '-r', str(root / 'build/android-test/OrbitPad-device-tests.apk'))
process = subprocess.Popen([*adb, 'shell', 'am', 'instrument', '-w', '-e', 'suite', args.suite,
                           'app.orbitpad.controller.test/.DeviceInstrumentation'],
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
lines, checks, performance = [], [], []
recording = None
for line in process.stdout:
    lines.append(line)
    print(line, end='', flush=True)
    if 'orbit_trace=launch-activity' in line:
        # MIUI blocks activity starts from background instrumentation until brought forward.
        run('shell', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-f', '0x10000000',
            '-n', 'app.orbitpad.controller/.MainActivity', stdout=subprocess.DEVNULL)
    if 'orbit_trace=record-liquid-motion' in line:
        recording = subprocess.Popen([*adb, 'shell', 'screenrecord', '--time-limit', '18', '--bit-rate', '6000000', '/sdcard/orbitpad-liquid-motion.mp4'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if 'orbit_glass=' in line:
        checks.append(line.strip().split('orbit_glass=', 1)[1])
    if 'orbit_glass_perf=' in line:
        performance.append(json.loads(line.strip().split('orbit_glass_perf=', 1)[1]))
    if 'orbit_trace=failed-screenshot' in line:
        with (out / 'failed.png').open('wb') as stream:
            run('exec-out', 'screencap', '-p', stdout=stream)
exit_code = process.wait(timeout=10)
if recording is not None:
    recording.wait(timeout=25)
    run('pull', '/sdcard/orbitpad-liquid-motion.mp4', str(out / 'liquid-motion.mp4'))
(out / 'instrumentation.txt').write_text(''.join(lines), encoding='utf-8')
run('pull', '/sdcard/Android/data/app.orbitpad.controller/files/glass-test/.', str(out))
model = subprocess.check_output([*adb, 'shell', 'getprop', 'ro.product.model'], text=True).strip()
api = subprocess.check_output([*adb, 'shell', 'getprop', 'ro.build.version.sdk'], text=True).strip()
apk = root / 'releases/OrbitPad-Android-v0.5.0.apk'
passed = exit_code == 0 and any('orbit_result=PASS' in line for line in lines) and (args.suite != 'glass' or len(performance) == 3)
hyperos = subprocess.check_output([*adb, 'shell', 'getprop', 'ro.mi.os.version.name'], text=True).strip()
report = {'ok': passed, 'model': model, 'api': int(api), 'hyperos': hyperos, 'suite': args.suite, 'checks': checks, 'performance': performance,
          'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
          'measurement': 'Native controller editor geometry, grouped gestures, independent saved previews, carousel switching and Gaussian fallback; functional checks, not a frame-rate benchmark.' if args.suite == 'layout' else 'Top Gaussian fade, foreground refraction and directional/interrupted page transitions.' if args.suite == 'polish' else 'Pixel comparisons during negative overscroll, mode changes, repeated crossings and release.' if args.suite == 'edge' else 'Scripted long presses, velocity changes, release/cancel, pointer loss and visibility changes.' if args.suite == 'motion' else 'Window FrameMetrics TOTAL_DURATION during eight alternating smooth scrolls per quality; smoke test, not Macrobenchmark.'}
(out / 'report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
print(f'{len(checks)} checks; {len(performance)} performance samples; result={"PASS" if passed else "FAIL"}')
sys.exit(0 if passed else 1)
