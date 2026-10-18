"""Fetch the Android build tools from Google's SDK repository into this project only."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.build-tools'
TOOLS.mkdir(exist_ok=True)
repository = TOOLS / 'repository.xml'
if not repository.exists():
    urllib.request.urlretrieve('https://dl.google.com/android/repository/repository2-1.xml', repository)
tree = ET.parse(repository).getroot()
requested = {'platforms;android-35': 'platform-35_r02.zip', 'build-tools;35.0.0': 'build-tools_r35_windows.zip'}

def install(item):
    package, name = item
    target = TOOLS / 'android-sdk' / package.replace(';', '/')
    if (target / ('android.jar' if package.startswith('platforms;') else 'aapt2.exe')).exists():
        print(f'Already available: {package}', flush=True)
        return
    record = next(c for p in tree.findall('remotePackage') if p.attrib.get('path') == package
                  for c in p.findall('./archives/archive/complete') if c.findtext('url') == name)
    archive = TOOLS / name
    expected = record.findtext('checksum')
    if not archive.exists() or hashlib.sha1(archive.read_bytes()).hexdigest() != expected:
        print(f'Downloading {name} from dl.google.com...', flush=True)
        urllib.request.urlretrieve('https://dl.google.com/android/repository/' + name, archive)
    if hashlib.sha1(archive.read_bytes()).hexdigest() != expected:
        raise RuntimeError(f'Google repository checksum mismatch: {name}')
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            parts = Path(info.filename).parts[1:]
            if not parts:
                continue
            destination = target.joinpath(*parts).resolve()
            if not destination.is_relative_to(target.resolve()):
                raise RuntimeError('Invalid archive path')
            if info.is_dir():
                destination.mkdir(parents=True, exist_ok=True)
            else:
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(z.read(info))
    print(f'Installed: {package}', flush=True)

with ThreadPoolExecutor(max_workers=2) as pool:
    list(pool.map(install, requested.items()))
