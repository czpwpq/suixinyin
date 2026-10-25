import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
jdk = Path(os.environ['JAVA_HOME'])
(root / 'build').mkdir(exist_ok=True)
out = Path(tempfile.mkdtemp(prefix='protocol-tests-', dir=root / 'build'))
subprocess.run([str(jdk / 'bin/javac'), '-encoding', 'UTF-8', '--release', '8', '-d', str(out),
    str(root / 'src/org/nfcresearch/qpolllab/PanelProtocol.java'),
    str(root / 'src/org/nfcresearch/qpolllab/PanelType.java'),
    str(root / 'tests/PanelTypeTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/PhotoAdjust.java'),
    str(root / 'tests/PhotoAdjustTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/FourColorDither.java'),
    str(root / 'src/org/nfcresearch/qpolllab/CalendarModel.java'),
    str(root / 'src/org/nfcresearch/qpolllab/RotationSchedule.java'),
    str(root / 'src/org/nfcresearch/qpolllab/TransferDisplay.java'),
    str(root / 'tests/FourColorDitherTest.java'),
    str(root / 'tests/CalendarModelTest.java'),
    str(root / 'tests/RotationScheduleTest.java'),
    str(root / 'tests/TransferDisplayTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/PhotoEdit.java'),
    str(root / 'src/org/nfcresearch/qpolllab/EditHistory.java'),
    str(root / 'src/org/nfcresearch/qpolllab/InkSightFitter.java'),
    str(root / 'src/org/nfcresearch/qpolllab/InkSightQuantizer.java'),
    str(root / 'tests/PhotoEditTest.java'),
    str(root / 'tests/EditSceneTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/CardScene.java'),
    str(root / 'tests/CardSceneTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/DailyContent.java'),
    str(root / 'tests/DailyContentTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/PushReceipt.java'),
    str(root / 'tests/PushReceiptTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/RefreshTestPattern.java'),
    str(root / 'tests/RefreshTestPatternTest.java'),
    str(root / 'tests/InkSightQuantizerTest.java'),
    str(root / 'tests/InkSightFitterTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/WeatherCode.java'),
    str(root / 'tests/WeatherCodeTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/WeatherCaption.java'),
    str(root / 'tests/WeatherCaptionTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/BridgeIdentity.java'),
    str(root / 'tests/BridgeIdentityTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/GalleryUrl.java'),
    str(root / 'tests/GalleryUrlTest.java'),
    str(root / 'src/org/nfcresearch/qpolllab/ImageBlocks.java'),
    str(root / 'src/org/nfcresearch/qpolllab/ImageRaster.java'),
    str(root / 'src/org/nfcresearch/qpolllab/CropWindow.java'),
    str(root / 'src/org/nfcresearch/qpolllab/ConnectionRetry.java'),
    str(root / 'src/org/nfcresearch/qpolllab/SessionFailure.java'),
    str(root / 'src/org/nfcresearch/qpolllab/NciDiscovery.java'),
    str(root / 'tests/ConnectionRetryTest.java'),
    str(root / 'tests/SessionFailureTest.java'),
    str(root / 'tests/NciDiscoveryTest.java'),
    str(root / 'tests/ProtocolTest.java'), str(root / 'tests/ImageBlocksTest.java'),
    str(root / 'tests/ImageRasterTest.java'), str(root / 'tests/CropWindowTest.java')], check=True)
subprocess.run([str(jdk / 'bin/java'), '-cp', str(out), 'org.nfcresearch.qpolllab.ProtocolTest', str(out)], check=True)
subprocess.run([str(jdk / 'bin/java'), '-cp', str(out), 'org.nfcresearch.qpolllab.ImageBlocksTest'], check=True)
subprocess.run([str(jdk / 'bin/java'), '-cp', str(out), 'org.nfcresearch.qpolllab.ImageRasterTest', str(out)], check=True)
subprocess.run([str(jdk / 'bin/java'), '-cp', str(out), 'org.nfcresearch.qpolllab.CropWindowTest'], check=True)
for test in ('GalleryUrlTest', 'BridgeIdentityTest', 'WeatherCodeTest', 'WeatherCaptionTest', 'FourColorDitherTest', 'CalendarModelTest', 'RotationScheduleTest', 'TransferDisplayTest', 'PhotoAdjustTest', 'PanelTypeTest', 'ConnectionRetryTest', 'SessionFailureTest', 'NciDiscoveryTest'):
    subprocess.run([str(jdk / 'bin/java'), '-cp', str(out), 'org.nfcresearch.qpolllab.' + test, str(out)], check=True)
for test in ('PhotoEditTest', 'EditSceneTest', 'CardSceneTest', 'DailyContentTest', 'PushReceiptTest', 'RefreshTestPatternTest', 'InkSightFitterTest'):
    subprocess.run([str(jdk / 'bin/java'), '-cp', str(out), 'org.nfcresearch.qpolllab.' + test], check=True)
subprocess.run([str(jdk / 'bin/java'), '-cp', str(out), 'org.nfcresearch.qpolllab.InkSightQuantizerTest', str(root / 'tests/inksight-fixtures')], check=True)
(root / 'build/last-test-dir.txt').write_text(out.name, encoding='utf-8')
print('FIXTURES:', out)
