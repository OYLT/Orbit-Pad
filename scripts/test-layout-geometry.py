"""Test the production editor's geometry without an Android device."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
candidates = [os.getenv('ORBIT_JAVA_HOME'), os.getenv('JAVA_HOME'),
              r'C:\Program Files\STMicroelectronics\STM32Cube\STM32CubeMX\jre',
              r'C:\Program Files\Android\Android Studio\jbr']
java = next((Path(p) for p in candidates if p and (Path(p) / 'bin/javac.exe').exists()), None)
if java is None:
    raise RuntimeError('Set JAVA_HOME to JDK 17 or newer.')
output = root / 'build/layout-geometry-test'
output.mkdir(parents=True, exist_ok=True)
subprocess.run([str(java / 'bin/javac.exe'), '-encoding', 'UTF-8', '--release', '8', '-d', str(output),
                str(root / 'android/app/src/main/java/app/orbitpad/controller/LayoutGeometry.java'),
                str(root / 'test/LayoutGeometryTest.java')], check=True, cwd=root)
subprocess.run([str(java / 'bin/java.exe'), '-cp', str(output), 'app.orbitpad.controller.LayoutGeometryTest'], check=True, cwd=root)
