"""Pinned local Java/AAR dependencies, JNI payloads and non-class runtime resources."""
from pathlib import Path
import zipfile

ABIS = {'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'}


def collect(root: Path, build: Path):
    libraries = []
    native = {}
    resources = {}
    required = {'api-13.1.5.aar', 'provider-13.1.5.aar', 'aidl-13.1.5.aar', 'shared-13.1.5.aar'}
    inputs = sorted((root / 'libs').glob('*.aar'))
    if not required.issubset({item.name for item in inputs}):
        raise RuntimeError('Missing pinned Shizuku API 13.1.5 dependencies')
    for aar in inputs:
        target = build / (aar.stem + '.jar')
        with zipfile.ZipFile(aar) as archive:
            target.write_bytes(archive.read('classes.jar'))
            for name in archive.namelist():
                parts = name.split('/')
                if len(parts) == 3 and parts[0] == 'jni' and parts[1] in ABIS and parts[2].endswith('.so'):
                    key = 'lib/' + '/'.join(parts[1:])
                    payload = archive.read(name)
                    if key in native and native[key] != payload:
                        raise RuntimeError('Conflicting native dependency: ' + key)
                    native[key] = payload
        # Compile this unchanged upstream JNI facade from src with our Java-8
        # compiler. The published enum class triggers a D8 34.0 internal error;
        # its native implementation remains the original pinned AAR payload.
        if aar.name != 'spake2-android-2.2.1.aar':
            libraries.append(target)
    libraries.extend(sorted((root / 'libs').glob('*.jar')))
    for library in libraries:
        with zipfile.ZipFile(library) as archive:
            for name in archive.namelist():
                upper = name.upper()
                if name.endswith('/') or name.endswith('.class') or name.startswith('META-INF/versions/'):
                    continue
                if name.startswith('/') or '..' in name.split('/'):
                    raise RuntimeError('Invalid dependency resource path')
                if upper.startswith('META-INF/') and not name.startswith('META-INF/services/'):
                    continue
                payload = archive.read(name)
                if name.startswith('META-INF/services/'):
                    resources[name] = resources.get(name, b'') + payload + b'\n'
                elif name in resources and resources[name] != payload:
                    raise RuntimeError('Conflicting dependency resource: ' + name)
                else:
                    resources[name] = payload
    return libraries, native, resources


def add_runtime(archive, native, resources):
    for name, payload in sorted({**resources, **native}.items()):
        if name in archive.namelist():
            raise RuntimeError('Duplicate APK runtime entry: ' + name)
        # Compressed JNI is extracted by Android; ELF segment alignment is checked separately.
        archive.writestr(name, payload, compress_type=zipfile.ZIP_DEFLATED)
