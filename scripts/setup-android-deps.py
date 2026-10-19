"""Fetch the pinned, offline ZXing QR decoder from Maven Central."""
from pathlib import Path
import hashlib
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
jar = ROOT / '.build-tools/zxing-core-3.5.3.jar'
expected = '8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82'
if not jar.exists() or hashlib.sha256(jar.read_bytes()).hexdigest() != expected:
    jar.parent.mkdir(exist_ok=True)
    urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar',jar)
if hashlib.sha256(jar.read_bytes()).hexdigest() != expected:
    raise RuntimeError('ZXing checksum mismatch')
print('ZXing 3.5.3 checksum verified.')
