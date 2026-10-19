"""Observe receiver state while the separately installed instrumentation APK touches the real phone."""
import argparse
import base64
import hashlib
import json
import pathlib
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--address', required=True)
parser.add_argument('--port', type=int, default=3215)
parser.add_argument('--output', default='artifacts/redmi-test')
parser.add_argument('--suite', choices=['baseline', 'settings', 'shell', 'usb', 'camera', 'bluetooth'], default='baseline')
parser.add_argument('--bluetooth-name', default='LAPTOP-MHB9SRM0')
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
output = (root / args.output).resolve()
output.mkdir(parents=True, exist_ok=True)
base = f'http://127.0.0.1:{args.port}'
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
def status():
    with opener.open(base + '/api/host', timeout=1.5) as response:
        return json.load(response)

host = status()
assert host['mode'] == 'preview', 'Touch suite must use a dry-run receiver.'
adb = ['adb', '-s', args.serial]
def prop(name):
    return subprocess.check_output([*adb, 'shell', 'getprop', name], text=True).strip()
manifest = ET.parse(root / 'android/app/src/main/AndroidManifest.xml').getroot()
version = manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']
package_info = subprocess.check_output([*adb, 'shell', 'dumpsys', 'package', 'app.orbitpad.controller'], text=True)
camera_granted_before = bool(re.search(r'android.permission.CAMERA: granted=true',package_info))
bluetooth_granted_before = bool(re.search(r'android.permission.BLUETOOTH_CONNECT: granted=true',package_info))
assert f'versionName={version}' in package_info, 'Install the current APK before running device tests.'
subprocess.run([*adb, 'install', '-r', str(root / 'build/android-test/OrbitPad-device-tests.apk')], check=True)
command = [*adb, 'shell', 'am', 'instrument', '-w', '-e', 'address', args.address,
           '-e', 'code', host['pairCode'], '-e', 'suite', args.suite,
           'app.orbitpad.controller.test/.DeviceInstrumentation']
if args.suite == 'shell':
    with opener.open(base + '/api/host/qr?format=png&ip=' + args.address.split(':')[0]) as response:
        qr = base64.b64encode(response.read()).decode('ascii')
    command[-1:-1] = ['-e', 'qrBase64', qr]
if args.suite == 'bluetooth':
    command[-1:-1] = ['-e', 'bluetoothName', args.bluetooth_name]
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
        if stage in ['wrong-code-rejected', 'landscape-awake-connected', 'three-finger-dual-stick-and-a', 'material-settings-mapping', 'material-settings-feel', 'settings-back-preserves-connection', 'native-settings-saved', 'native-settings-restored', 'shell-home', 'shell-theme', 'shell-about', 'shell-scan-default', 'shell-editor-drag', 'shell-template-loaded', 'shell-usb-option', 'shell-bluetooth-option']:
            shot = output / (stage + '.png')
            with shot.open('wb') as file:
                subprocess.run([*adb, 'exec-out', 'screencap', '-p'], stdout=file, check=True)
        bundle = {}
exit_code = device.wait(timeout=10)
if args.suite == 'camera' and not camera_granted_before:
    subprocess.run([*adb, 'shell', 'pm', 'revoke', 'app.orbitpad.controller', 'android.permission.CAMERA'],check=True)
if args.suite == 'bluetooth' and not bluetooth_granted_before:
    subprocess.run([*adb, 'shell', 'pm', 'revoke', 'app.orbitpad.controller', 'android.permission.BLUETOOTH_CONNECT'],check=True)
for touch in single_touches:
    assert touch.wait(timeout=5) == 0, 'Android single-finger input command failed'
(output / 'instrumentation.txt').write_text(''.join(log), encoding='utf-8')
passed = exit_code == 0 and any('orbit_result=PASS' in line for line in log) and all(p['passed'] for p in phases)
report = {'device': 'Redmi K40 Pro+', 'model': prop('ro.product.model'), 'serial': args.serial,
          'android': prop('ro.build.version.release'), 'api': prop('ro.build.version.sdk'),
          'abi': prop('ro.product.cpu.abi'), 'version': version,
          'apkSha256': hashlib.sha256((root / f'releases/OrbitPad-Android-v{version}.apk').read_bytes()).hexdigest(),
          'transport': {'usb': 'USB', 'bluetooth': 'Bluetooth RFCOMM', 'camera': 'none'}.get(args.suite,'Wi-Fi'), 'address': args.address,
          'suite': args.suite,
          'receiverMode': host['mode'], 'ok': passed, 'phases': phases, 'instrumentationExitCode': exit_code}
(output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'{len(phases)} phases; result={"PASS" if passed else "FAIL"}', flush=True)
if not passed:
    print(''.join(log[-25:]), flush=True)
    raise SystemExit(1)
