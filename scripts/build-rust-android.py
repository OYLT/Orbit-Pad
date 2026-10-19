"""Compile the Rust controller into both 64-bit Android ABIs, without Gradle."""
from pathlib import Path
import json
import os
import shutil
import subprocess
import tomllib

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.build-tools'
NDK = Path(os.getenv('ANDROID_NDK_HOME') or TOOLS / 'android-sdk/ndk/28.2.13676358')
LLVM = NDK / 'toolchains/llvm/prebuilt/windows-x86_64/bin'
CARGO = TOOLS / 'cargo/bin/cargo.exe'
env = dict(os.environ, CARGO_HOME=str(TOOLS / 'cargo'), RUSTUP_HOME=str(TOOLS / 'rustup'),
           PATH=str(TOOLS / 'cargo/bin') + os.pathsep + os.environ['PATH'])
version = tomllib.loads((ROOT / 'android/rust/Cargo.toml').read_text(encoding='utf-8'))['package']['version']
report = {'version': version, 'rust': '1.90.0', 'ndk': '28.2.13676358', 'libraries': []}
for target, abi, compiler in [('aarch64-linux-android', 'arm64-v8a', 'aarch64-linux-android26-clang.cmd'),
                              ('x86_64-linux-android', 'x86_64', 'x86_64-linux-android26-clang.cmd')]:
    target_key = target.upper().replace('-', '_')
    env['CARGO_TARGET_' + target_key + '_LINKER'] = str(LLVM / compiler)
    env['CARGO_TARGET_' + target_key + '_RUSTFLAGS'] = '-C link-arg=-Wl,-z,max-page-size=16384'
    subprocess.run([str(CARGO), '+1.90.0', 'build', '--locked', '--release', '--target', target,
                    '--manifest-path', str(ROOT / 'android/rust/Cargo.toml')], env=env, cwd=ROOT, check=True)
    library = ROOT / 'android/rust/target' / target / 'release/liborbitpad.so'
    headers = subprocess.run([str(LLVM / 'llvm-readelf.exe'), '-lW', str(library)], capture_output=True, text=True, check=True).stdout
    loads = [line.split()[-1] for line in headers.splitlines() if line.strip().startswith('LOAD ')]
    assert loads and all(int(n, 16) >= 16384 for n in loads), 'Native library must support 16 KB pages'
    symbols = subprocess.run([str(LLVM / 'llvm-nm.exe'), '-D', str(library)], capture_output=True, text=True, check=True).stdout
    assert 'Java_app_orbitpad_controller_NativeBridge_call' in symbols, 'Missing JNI entry point'
    destination = ROOT / 'build/android/jni' / abi / library.name
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(library, destination)
    report['libraries'].append({'abi': abi, 'size': library.stat().st_size, 'loadAlignment': loads, 'jni': True})
(ROOT / 'releases').mkdir(exist_ok=True)
(ROOT / 'releases/android-rust-build.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report, indent=2))
