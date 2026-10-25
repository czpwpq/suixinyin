#!/usr/bin/env python3
"""Real host-side pairing primitives, independent of Android UI and network service."""
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[2]
JDK=Path(os.environ['JAVA_HOME'])
SDK=Path(os.environ['ANDROID_HOME'])
OUT=Path(tempfile.mkdtemp(prefix='adb-crypto-tests-',dir=ROOT/'build'))
SOURCE=ROOT/'src'
CP=os.pathsep.join(map(str,[SDK/'platforms/android-34/android.jar',ROOT/'libs/annotation-1.3.0.jar',ROOT/'libs/bcprov-jdk15to18-1.81.jar']))
sources = [SOURCE/'io/github/muntashirakon/adb'/name for name in ['StringCompat.java','AndroidPubkey.java','ByteArrayNoThrowOutputStream.java']]
sources += [SOURCE/'org/nfcresearch/qpolllab/activation/AdbIdentityMaterial.java',ROOT/'tests/activation/PairingCryptoTest.java']
def run(tool,*args):
    target=JDK/'bin'/tool
    if os.name=='nt':target=target.with_suffix('.exe')
    subprocess.run([str(target),*map(str,args)],check=True)
run('javac','-encoding','UTF-8','--release','8','-classpath',CP,'-d',OUT,*sources)
run('java','-cp',str(OUT)+os.pathsep+CP,'io.github.muntashirakon.adb.PairingCryptoTest')
socket_sources=['AdbConnection.java','AdbStream.java','AdbInputStream.java','AdbOutputStream.java','AdbProtocol.java','KeyPair.java','SslUtils.java','LocalServices.java','AdbAuthenticationFailedException.java','AdbPairingRequiredException.java']
run('javac','-encoding','UTF-8','--release','8','-classpath',str(OUT)+os.pathsep+CP,'-d',OUT,
    *[SOURCE/'io/github/muntashirakon/adb'/name for name in socket_sources],
    *list((ROOT/'tests/activation/stubs').rglob('*.java')),ROOT/'tests/activation/AdbSocketTest.java')
run('java','-cp',str(OUT)+os.pathsep+CP,'io.github.muntashirakon.adb.AdbSocketTest')
# Check the exact native artifacts that will be shipped, not only APK ZIP alignment.
import struct
for artifact,library in [('conscrypt-android-2.5.3.aar','libconscrypt_jni.so'),('spake2-android-2.2.1.aar','libspake2.so')]:
    with zipfile.ZipFile(ROOT/'libs'/artifact) as archive:
        for abi in ['arm64-v8a','x86_64']:
            data=archive.read('jni/'+abi+'/'+library)
            assert data[:6]==b'\x7fELF\x02\x01'
            offset=struct.unpack_from('<Q',data,32)[0]
            size,count=struct.unpack_from('<HH',data,54)
            loads=[struct.unpack_from('<Q',data,offset+i*size+48)[0] for i in range(count)
                   if struct.unpack_from('<I',data,offset+i*size)[0]==1]
            assert loads and all(a>=16384 for a in loads),(abi,loads)
            print('PASS native ELF LOAD alignment:',library,abi,','.join(hex(a) for a in loads))
print('ADB_CRYPTO_HOST_PASS',OUT)
