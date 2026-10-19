"""Install an isolated Rust/NDK toolchain. Never modifies the user's PATH."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import os
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.build-tools'
RUST_VERSION = '1.90.0'
NDK_VERSION = '28.2.13676358'
HOST = 'x86_64-pc-windows-gnu'


def rust_env():
    return dict(os.environ, CARGO_HOME=str(TOOLS / 'cargo'), RUSTUP_HOME=str(TOOLS / 'rustup'),
                PATH=str(TOOLS / 'cargo/bin') + os.pathsep + os.environ['PATH'])


def download(url, destination, algorithm='sha256', expected=None):
    if expected is None:
        expected = urllib.request.urlopen(url + '.sha256', timeout=60).read().decode().split()[0]
    if destination.exists() and hashlib.new(algorithm, destination.read_bytes()).hexdigest() == expected:
        return
    print('Downloading ' + destination.name, flush=True)
    urllib.request.urlretrieve(url, destination)
    if hashlib.new(algorithm, destination.read_bytes()).hexdigest() != expected:
        raise RuntimeError('Checksum mismatch: ' + destination.name)


def install_ndk():
    target = TOOLS / 'android-sdk/ndk' / NDK_VERSION
    if (target / 'source.properties').exists():
        return
    tree = ET.parse(TOOLS / 'repository.xml').getroot()
    package = next(p for p in tree.findall('remotePackage') if p.attrib['path'] == 'ndk;' + NDK_VERSION)
    record = next(a.find('complete') for a in package.findall('./archives/archive') if a.findtext('host-os') == 'windows')
    archive = TOOLS / record.findtext('url')
    download('https://dl.google.com/android/repository/' + archive.name, archive, 'sha1', record.findtext('checksum'))
    print('Extracting Android NDK...', flush=True)
    with zipfile.ZipFile(archive) as z:
        for item in z.infolist():
            parts = Path(item.filename).parts[1:]
            if not parts:
                continue
            destination = target.joinpath(*parts).resolve()
            if not destination.is_relative_to(target.resolve()):
                raise RuntimeError('Invalid archive path')
            if item.is_dir():
                destination.mkdir(parents=True, exist_ok=True)
            else:
                destination.parent.mkdir(parents=True, exist_ok=True)
                with z.open(item) as source, destination.open('wb') as out:
                    import shutil
                    shutil.copyfileobj(source, out)


def install_rust():
    rustup = TOOLS / 'cargo/bin/rustup.exe'
    if not rustup.exists():
        installer = TOOLS / 'rustup-init.exe'
        download('https://static.rust-lang.org/rustup/dist/' + HOST + '/rustup-init.exe', installer)
        subprocess.run([str(installer), '-y', '--no-modify-path', '--profile', 'minimal',
                        '--default-host', HOST, '--default-toolchain', RUST_VERSION], env=rust_env(), check=True)
    subprocess.run([str(rustup), 'toolchain', 'install', RUST_VERSION, '--profile', 'minimal',
                    '--component', 'rustfmt,clippy'], env=rust_env(), check=True)
    subprocess.run([str(rustup), 'target', 'add', '--toolchain', RUST_VERSION,
                    'aarch64-linux-android', 'x86_64-linux-android'], env=rust_env(), check=True)


if __name__ == '__main__':
    TOOLS.mkdir(exist_ok=True)
    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(lambda f: f(), [install_ndk, install_rust]))
    print('Rust and Android NDK ready.', flush=True)
