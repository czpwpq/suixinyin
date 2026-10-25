#!/usr/bin/env python3
"""Offline reader tests: fake APDU transport, independent LZO decode, and actual text/search helpers."""
import os
from pathlib import Path
import subprocess
import tempfile
from check_lzo import verify

ROOT = Path(__file__).resolve().parents[2]
TEST = Path(__file__).resolve().parent
JDK = Path(os.environ['JAVA_HOME'])
SDK = Path(os.environ['ANDROID_HOME'])
BUILD = ROOT / 'build'
BUILD.mkdir(exist_ok=True)
OUT = Path(tempfile.mkdtemp(prefix='reader-tests-', dir=BUILD))
ENV = dict(os.environ, JAVA_HOME=str(JDK), PATH=str(JDK / 'bin') + os.pathsep + os.environ.get('PATH', ''))
SOURCE = ROOT / 'src/org/nfcresearch/qpolllab'
READER = ROOT / 'src/com/inkreader'
ANDROID = SDK / 'platforms/android-34/android.jar'


def run(*args):
    args = [str(arg) for arg in args]
    if os.name == 'nt' and Path(args[0]).parent == JDK / 'bin':
        args[0] += '.exe'
    subprocess.run(args, env=ENV, check=True)


protocol_tests = ['ReaderProtocolTest', 'ReaderPanelTypeTest', 'ReaderPreloadTest', 'ReaderLzoCompressionTest']
run(JDK / 'bin/javac', '-encoding', 'UTF-8', '--release', '8', '-d', OUT,
    *[SOURCE / (name + '.java') for name in ['ReaderPanelProtocol', 'PanelProtocol', 'PanelType', 'ImageRaster', 'ImageBlocks']],
    *[TEST / (name + '.java') for name in protocol_tests], TEST / 'ReaderPacketCountProbe.java')
for test in protocol_tests:
    run(JDK / 'bin/java', '-cp', OUT, 'org.nfcresearch.qpolllab.' + test, OUT)
verify(OUT, Path(os.environ.get('READER_TEST_DEPS', str(ROOT.parent / 'reader-test-deps'))))

# SQLite is replaced only at the storage boundary for BookSearch. Production import helpers still run.
TEXT_OUT = OUT / 'text'
TEXT_OUT.mkdir()
text_sources = ['Book', 'SearchHit', 'SearchSegment', 'BookSearch', 'ChapterDetector', 'ImportData', 'TxtImporter']
run(JDK / 'bin/javac', '-encoding', 'UTF-8', '--release', '8', '-classpath', ANDROID,
    '-d', TEXT_OUT, *[READER / (name + '.java') for name in text_sources],
    TEST / 'stubs/com/inkreader/ReaderDb.java', TEST / 'ChapterDetectorTest.java',
    TEST / 'BookSearchTest.java', TEST / 'TxtEncodingTest.java')
for test in ['ChapterDetectorTest', 'BookSearchTest', 'TxtEncodingTest']:
    run(JDK / 'bin/java', '-cp', str(TEXT_OUT) + os.pathsep + str(ANDROID), 'com.inkreader.' + test, OUT)
run(JDK / 'bin/javac', '-encoding', 'UTF-8', '--release', '8', '-classpath', ANDROID,
    '-d', TEXT_OUT, SOURCE / 'NfcSessionLock.java', TEST / 'ReaderSessionLockTest.java')
run(JDK / 'bin/java', '-cp', str(TEXT_OUT) + os.pathsep + str(ANDROID),
    'org.nfcresearch.qpolllab.ReaderSessionLockTest')
print('ALL_READER_OFFLINE_TESTS_PASS', OUT, flush=True)
print('These are JVM/fake-transport checks, not Android UI, Shizuku, NFC or physical partial-refresh results.', flush=True)
