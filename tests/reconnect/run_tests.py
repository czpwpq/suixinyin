"""Run the production recovery helper against finite clock/storage/NSD/controller boundaries."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[2]
TEST=Path(__file__).resolve().parent
JDK=Path(os.environ['JAVA_HOME'])
(ROOT/'build').mkdir(exist_ok=True)
OUT=Path(tempfile.mkdtemp(prefix='reconnect-tests-',dir=ROOT/'build'))
suffix='.exe' if os.name=='nt' else ''
sources=[ROOT/'src/org/nfcresearch/qpolllab/InternalReconnect.java',
         ROOT/'src/org/nfcresearch/qpolllab/BackendMode.java',
         ROOT/'src/org/nfcresearch/qpolllab/activation/ReconnectPlan.java',
         TEST/'InternalReconnectTest.java',*sorted((TEST/'stubs').rglob('*.java'))]
subprocess.run([str(JDK/'bin'/('javac'+suffix)),'-encoding','UTF-8','--release','8','-d',str(OUT),*map(str,sources)],check=True)
subprocess.run([str(JDK/'bin'/('java'+suffix)),'-cp',str(OUT),'org.nfcresearch.qpolllab.InternalReconnectTest'],check=True)
