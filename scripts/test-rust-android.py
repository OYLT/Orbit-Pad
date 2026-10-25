"""Run Rust checks and the actual Rust client against the production receiver protocol."""
from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.build-tools'
env = dict(os.environ, CARGO_HOME=str(TOOLS / 'cargo'), RUSTUP_HOME=str(TOOLS / 'rustup'),
           PATH=str(TOOLS / 'cargo/bin') + os.pathsep + os.environ['PATH'])
cargo = str(TOOLS / 'cargo/bin/cargo.exe')
manifest = str(ROOT / 'android/rust/Cargo.toml')
subprocess.run([sys.executable, ROOT / 'scripts/test-gyro-steering.py'], cwd=ROOT, check=True)
for args in [['fmt', '--manifest-path', manifest, '--check'],
             ['test', '--locked', '--manifest-path', manifest],
             ['clippy', '--locked', '--all-targets', '--manifest-path', manifest, '--', '-D', 'warnings'],
             ['build', '--locked', '--example', 'core-driver', '--manifest-path', manifest]]:
    subprocess.run([cargo, '+1.90.0', *args], env=env, cwd=ROOT, check=True)
subprocess.run(['node', 'scripts/verify-rust-protocol.mjs'], cwd=ROOT, check=True)
