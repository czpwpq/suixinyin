#!/usr/bin/env python3
"""Build a separately installed renderer instrumentation APK with the main APK signing key."""
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile
import sys

root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root))
from build_dependencies import collect
sdk, jdk = Path(os.environ['ANDROID_HOME']), Path(os.environ['JAVA_HOME'])
android = sdk / 'platforms/android-34/android.jar'
bt = sdk / 'build-tools/34.0.0'
build_root = root / 'build'
build_root.mkdir(exist_ok=True)
build = Path(tempfile.mkdtemp(prefix='card-instrumentation-', dir=build_root))
support, classes, dex = build / 'support', build / 'classes', build / 'dex'
for folder in (support, classes, dex, root / 'dist'):
    folder.mkdir(exist_ok=True)
env = dict(os.environ, JAVA_HOME=str(jdk), PATH=str(jdk / 'bin') + os.pathsep + os.environ.get('PATH', ''))


def run(*args):
    argv = [str(arg) for arg in args]
    if os.name == 'nt':
        tool = Path(argv[0])
        if tool.parent == jdk / 'bin':
            argv[0] += '.exe'
        elif tool.parent == bt:
            argv[0] += '.bat' if tool.name in ('d8', 'apksigner') else '.exe'
    subprocess.run(argv, cwd=root, env=env, check=True)


def jar(folder, target):
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        for item in folder.rglob('*.class'):
            archive.write(item, item.relative_to(folder).as_posix())


source = root / 'src'
libraries, _, _ = collect(root, build)
run(jdk / 'bin/javac', '-encoding', 'UTF-8', '--release', '8', '-classpath',
    os.pathsep.join(map(str,[android,*libraries])), '-d', support, *sorted(source.rglob('*.java')))
jar(support, build / 'support.jar')
run(jdk / 'bin/javac', '-encoding', 'UTF-8', '--release', '8', '-classpath',
    os.pathsep.join(map(str, [android,support,*libraries])), '-d', classes,
    *sorted((root / 'tests/android').glob('*.java')))
jar(classes, build / 'tests.jar')
run(bt / 'd8', '--min-api', '26', '--lib', android, '--classpath', build / 'support.jar',
    '--output', dex, build / 'tests.jar')
run(bt / 'aapt', 'package', '-f', '-M', root / 'tests/android/AndroidManifest.xml',
    '-I', android, '-F', build / 'unsigned.apk')
with zipfile.ZipFile(build / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
    archive.write(dex / 'classes.dex', 'classes.dex')
run(bt / 'zipalign', '-f', '4', build / 'unsigned.apk', build / 'aligned.apk')
key = Path(os.environ.get('QPOLLLAB_KEYSTORE', str(build_root / 'debug.keystore')))
if not key.exists():
    raise RuntimeError('Build the main APK first, then sign tests with the same keystore')
env['QPOLLLAB_SIGN_STORE'] = os.environ.get('QPOLLLAB_STORE_PASSWORD', 'android')
env['QPOLLLAB_SIGN_KEY'] = os.environ.get('QPOLLLAB_KEY_PASSWORD', env['QPOLLLAB_SIGN_STORE'])
target = root / 'dist/QPollLab-card-tests.apk'
run(bt / 'apksigner', 'sign', '--ks', key, '--ks-key-alias', os.environ.get('QPOLLLAB_KEY_ALIAS', 'qpolllab'),
    '--ks-pass', 'env:QPOLLLAB_SIGN_STORE', '--key-pass', 'env:QPOLLLAB_SIGN_KEY',
    '--out', target, build / 'aligned.apk')
run(bt / 'apksigner', 'verify', '--verbose', target)
print('Renderer test APK:', target)
print('This only builds the tests; run them on a connected Android device before claiming native rendering passed.')
