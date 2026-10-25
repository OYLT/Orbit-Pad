"""Exercise the production Java gravity math without a phone or emulator."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
candidates = [os.getenv('ORBIT_JAVA_HOME'), os.getenv('JAVA_HOME'),
              r'C:\Program Files\STMicroelectronics\STM32Cube\STM32CubeMX\jre',
              r'C:\Program Files\Android\Android Studio\jbr']
java = next((Path(p) for p in candidates if p and (Path(p) / 'bin/javac.exe').exists()), None)
if java is None:
    raise RuntimeError('Set JAVA_HOME to a JDK 17 or newer.')
platform = ROOT / '.build-tools/android-sdk/platforms/android-35/android.jar'
classes = ROOT / 'build/gyro-test'
classes.mkdir(parents=True, exist_ok=True)
subprocess.run([str(java / 'bin/javac.exe'), '-encoding', 'UTF-8', '--release', '8',
                '-classpath', str(platform), '-d', str(classes),
                str(ROOT / 'android/app/src/main/java/app/orbitpad/controller/GyroSteering.java'),
                str(ROOT / 'test/GyroSteeringTest.java')], cwd=ROOT, check=True)
subprocess.run([str(java / 'bin/java.exe'), '-cp', os.pathsep.join(map(str, [classes, platform])),
                'app.orbitpad.controller.GyroSteeringTest'], cwd=ROOT, check=True)
