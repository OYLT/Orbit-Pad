"""Observe receiver state while the separately installed instrumentation APK touches the real phone."""
import argparse
import json
import pathlib
import re
import subprocess
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--address', required=True)
parser.add_argument('--port', type=int, default=3215)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
output = root / 'artifacts/redmi-test'
output.mkdir(parents=True, exist_ok=True)
base = f'http://127.0.0.1:{args.port}'
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
def status():
    with opener.open(base + '/api/host', timeout=1.5) as response:
        return json.load(response)

host = status()
assert host['mode'] == 'preview', 'Touch suite must use a dry-run receiver.'
adb = ['adb', '-s', args.serial]
subprocess.run([*adb, 'install', '-r', str(root / 'build/android-test/OrbitPad-device-tests.apk')], check=True)
command = [*adb, 'shell', 'am', 'instrument', '-w', '-e', 'address', args.address,
           '-e', 'code', host['pairCode'], 'app.orbitpad.controller.test/.DeviceInstrumentation']
device = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
phases = []
bundle = {}
log = []
single_touches = []
for line in device.stdout:
    log.append(line)
    match = re.match(r'INSTRUMENTATION_STATUS: (\w+)=(.*)', line.rstrip())
    if match:
        bundle[match[1]] = match[2]
        if match[1] == 'orbit_trace': print('TRACE: ' + match[2], flush=True)
        if match[1] == 'orbit_trace' and re.fullmatch(r'single-touch:[0-9]+:[0-9]+', match[2]):
            _, x, y = match[2].split(':')
            single_touches.append(subprocess.Popen([*adb, 'shell', 'input', 'swipe', x, y, x, y, '700'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
        if match[1] == 'orbit_trace' and match[2] in ['launch-activity', 'connect-invalid-code', 'resume-activity']:
            flags = '0x10020000' if match[2] == 'resume-activity' else '0x10000000'
            subprocess.run([*adb, 'shell', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-f', flags,
                            '-n', 'app.orbitpad.controller/.MainActivity'], check=True, stdout=subprocess.DEVNULL)
        if match[1] == 'orbit_trace' and match[2] == 'failed-screenshot':
            with (output / 'failed.png').open('wb') as file:
                subprocess.run([*adb, 'exec-out', 'screencap', '-p'], stdout=file, check=True)
    if line.startswith('INSTRUMENTATION_STATUS_CODE:') and 'orbit_phase' in bundle:
        stage = bundle['orbit_phase']
        expected = set(filter(None, bundle.get('orbit_expected', '').split(',')))
        connected = bundle['orbit_connected'] == 'true'
        deadline = time.monotonic() + .42
        samples = []
        while True:
            actual = status()
            samples.append({'keys': actual['keys'], 'connected': actual['connected'], 'axes': actual['axes'], 'buttons': actual['buttons']})
            passed = set(actual['keys']) == expected and actual['connected'] == connected
            if passed or time.monotonic() >= deadline:
                break
            time.sleep(.035)
        phases.append({'phase': stage, 'passed': passed, 'expected': sorted(expected), 'expectedConnected': connected, 'samples': samples, 'emittedMs': bundle.get('orbit_emitted_ms'), 'receivedMs': round(time.time()*1000)})
        print(f'{"PASS" if passed else "FAIL"}: {stage}; keys={actual["keys"]}; connected={actual["connected"]}', flush=True)
        if stage in ['landscape-awake-connected', 'three-finger-dual-stick-and-a', 'material-settings-mapping', 'material-settings-feel', 'settings-back-preserves-connection']:
            shot = output / (stage + '.png')
            with shot.open('wb') as file:
                subprocess.run([*adb, 'exec-out', 'screencap', '-p'], stdout=file, check=True)
        bundle = {}
exit_code = device.wait(timeout=10)
for touch in single_touches:
    assert touch.wait(timeout=5) == 0, 'Android single-finger input command failed'
(output / 'instrumentation.txt').write_text(''.join(log), encoding='utf-8')
passed = exit_code == 0 and any('orbit_result=PASS' in line for line in log) and all(p['passed'] for p in phases)
report = {'device': 'Redmi K40 Pro+', 'android': 16, 'transport': 'Wi-Fi', 'address': args.address,
          'receiverMode': host['mode'], 'ok': passed, 'phases': phases, 'instrumentationExitCode': exit_code}
(output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'{len(phases)} phases; result={"PASS" if passed else "FAIL"}', flush=True)
if not passed:
    print(''.join(log[-25:]), flush=True)
    raise SystemExit(1)
