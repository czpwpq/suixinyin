from pathlib import Path
import os
import subprocess
import tempfile
import sys
root=Path(__file__).resolve().parents[2]
jdk=Path(os.environ['JAVA_HOME'])
android=Path(os.environ['ANDROID_HOME'])/'platforms/android-34/android.jar'
output=Path(tempfile.mkdtemp(prefix='activation-tests-',dir=root/'build'))
suffix='.exe' if os.name=='nt' else ''
subprocess.run([str(jdk/'bin'/('javac'+suffix)),'-encoding','UTF-8','--release','8','-cp',str(android),'-d',str(output),str(root/'src/org/nfcresearch/qpolllab/activation/ActivationInputs.java'),str(root/'src/org/nfcresearch/qpolllab/activation/ActivationProof.java'),str(root/'src/org/nfcresearch/qpolllab/activation/NotificationPairingInput.java'),str(root/'src/org/nfcresearch/qpolllab/BackendMode.java'),str(Path(__file__).parent/'ActivationInputsTest.java'),str(Path(__file__).parent/'ActivationProofTest.java'),str(Path(__file__).parent/'NotificationPairingInputTest.java')],check=True)
subprocess.run([str(jdk/'bin'/('java'+suffix)),'-cp',str(output)+os.pathsep+str(android),'org.nfcresearch.qpolllab.activation.ActivationInputsTest'],check=True)
subprocess.run([str(jdk/'bin'/('java'+suffix)),'-cp',str(output),'org.nfcresearch.qpolllab.activation.ActivationProofTest'],check=True)
subprocess.run([str(jdk/'bin'/('javac'+suffix)),'-encoding','UTF-8','--release','8','-cp',str(output),'-d',str(output),str(Path(__file__).parent/'ActivationCommandFixture.java')],check=True)
from check_launch_shell import verify as verify_launch_shell
verify_launch_shell(jdk/'bin'/('java'+suffix),output)
subprocess.run([str(jdk/'bin'/('javac'+suffix)),'-encoding','UTF-8','--release','8','-d',str(output),str(root/'src/org/nfcresearch/qpolllab/activation/AdbDiscoveryState.java'),str(Path(__file__).parent/'AdbDiscoveryStateTest.java')],check=True)
subprocess.run([str(jdk/'bin'/('java'+suffix)),'-cp',str(output),'org.nfcresearch.qpolllab.activation.AdbDiscoveryStateTest'],check=True)
subprocess.run([str(jdk/'bin'/('java'+suffix)),'-cp',str(output),'org.nfcresearch.qpolllab.activation.NotificationPairingInputTest'],check=True)
subprocess.run([str(jdk/'bin'/('javac'+suffix)),'-encoding','UTF-8','--release','8','-d',str(output),str(root/'src/org/nfcresearch/qpolllab/activation/ActivationStartupLog.java'),str(Path(__file__).parent/'ActivationStartupLogTest.java')],check=True)
subprocess.run([str(jdk/'bin'/('java'+suffix)),'-cp',str(output),'org.nfcresearch.qpolllab.activation.ActivationStartupLogTest'],check=True)
subprocess.run([str(jdk/'bin'/('javac'+suffix)),'-encoding','UTF-8','--release','8','-d',str(output),str(root/'src/org/nfcresearch/qpolllab/activation/ReconnectPlan.java'),str(Path(__file__).parent/'ReconnectPlanTest.java')],check=True)
subprocess.run([str(jdk/'bin'/('java'+suffix)),'-cp',str(output),'org.nfcresearch.qpolllab.activation.ReconnectPlanTest'],check=True)
subprocess.run([sys.executable,str(root/'tests/reconnect/run_tests.py')],check=True)
print('Activation host checks only; Android NSD, notifications, TLS pairing and NFC still require a device.')
