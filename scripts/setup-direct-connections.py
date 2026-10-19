"""Fetch official Android platform tools for the desktop's physical USB adapter."""
from pathlib import Path
import hashlib
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.build-tools'
archive = TOOLS / 'platform-tools_r37.0.1-win.zip'
expected = 'e03e78b1d80b396f1c3358e31251cb31740e1110'
if not archive.exists() or hashlib.sha1(archive.read_bytes()).hexdigest() != expected:
    urllib.request.urlretrieve('https://dl.google.com/android/repository/' + archive.name,archive)
if hashlib.sha1(archive.read_bytes()).hexdigest() != expected:
    raise RuntimeError('Platform tools checksum mismatch')
target = TOOLS / 'android-sdk'
with zipfile.ZipFile(archive) as z:
    for item in z.infolist():
        destination = (target / item.filename).resolve()
        if not destination.is_relative_to((target / 'platform-tools').resolve()):
            raise RuntimeError('Invalid SDK archive path')
        if item.is_dir():
            destination.mkdir(parents=True,exist_ok=True)
        else:
            destination.parent.mkdir(parents=True,exist_ok=True)
            with z.open(item) as source, destination.open('wb') as output:
                shutil.copyfileobj(source,output)
print('Official Android platform tools ready.')
