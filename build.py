#!/usr/bin/env python3
"""Build with the installed SDK/JDK and pinned local libraries; no Gradle or downloads."""
import hashlib
import os
from pathlib import Path
import subprocess
import zipfile
import tempfile
import xml.etree.ElementTree as ET
from build_dependencies import collect, add_runtime

root = Path(__file__).resolve().parent
sdk = Path(os.environ["ANDROID_HOME"])
jdk = Path(os.environ["JAVA_HOME"])
bt = sdk / "build-tools/34.0.0"
android = sdk / "platforms/android-34/android.jar"
build_root = root / "build"
build_root.mkdir(parents=True, exist_ok=True)
build = Path(tempfile.mkdtemp(prefix="v2-", dir=build_root))
dist = root / "dist"
for directory in (build / "classes", build / "dex", dist):
    directory.mkdir(parents=True, exist_ok=True)
env = dict(os.environ, JAVA_HOME=str(jdk), PATH=str(jdk / "bin") + os.pathsep + os.environ.get("PATH", ""))
libraries, native_libraries, dependency_resources = collect(root, build)

def run(*args, cwd=root):
    argv = [str(arg) for arg in args]
    if os.name == "nt":
        tool = Path(argv[0])
        if tool.parent == jdk / "bin":
            argv[0] += ".exe"
        elif tool.parent == bt:
            argv[0] += ".bat" if tool.name in ("d8", "apksigner") else ".exe"
    subprocess.run(argv, cwd=cwd, env=env, check=True)

run(jdk / "bin/javac", "-encoding", "UTF-8", "--release", "8",
    "-classpath", os.pathsep.join(str(p) for p in [android, *libraries]), "-d", build / "classes", *sorted((root / "src").rglob("*.java")))
with zipfile.ZipFile(build / "classes.jar", "w", zipfile.ZIP_DEFLATED) as archive:
    for item in sorted((build / "classes").rglob("*.class")):
        archive.write(item, item.relative_to(build / "classes").as_posix())
run(bt / "d8", "--min-api", "26", "--lib", android, "--output", build / "dex", build / "classes.jar", *libraries)
run(bt / "aapt", "package", "-f", "-M", root / "AndroidManifest.xml", "-S", root / "res",
    "-A", root / "assets", "-I", android, "-F", build / "unsigned.apk")
with zipfile.ZipFile(build / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as archive:
    for dex in sorted((build / "dex").glob("*.dex")):
        archive.write(dex, dex.name)
    add_runtime(archive, native_libraries, dependency_resources)
run(bt / "zipalign", "-f", "4", build / "unsigned.apk", build / "aligned.apk")
key = Path(os.environ.get("QPOLLLAB_KEYSTORE", str(build_root / "debug.keystore")))
storepass = os.environ.get("QPOLLLAB_STORE_PASSWORD", "android")
keypass = os.environ.get("QPOLLLAB_KEY_PASSWORD", storepass)
alias = os.environ.get("QPOLLLAB_KEY_ALIAS", "qpolllab")
if not key.exists() and "QPOLLLAB_KEYSTORE" in os.environ:
    raise RuntimeError("Configured signing keystore does not exist")
if not key.exists():
    run(jdk / "bin/keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android",
        "-alias", "qpolllab", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
        "-dname", "CN=QPoll Lab Local Debug", "-noprompt")
version = ET.parse(root / "AndroidManifest.xml").getroot().attrib["{http://schemas.android.com/apk/res/android}versionName"]
apk = dist / ("QPollLab-" + version + ".apk")
env["QPOLLLAB_SIGN_STORE"] = storepass
env["QPOLLLAB_SIGN_KEY"] = keypass
run(bt / "apksigner", "sign", "--ks", key, "--ks-key-alias", alias, "--ks-pass", "env:QPOLLLAB_SIGN_STORE",
    "--key-pass", "env:QPOLLLAB_SIGN_KEY", "--out", apk, build / "aligned.apk")
run(bt / "apksigner", "verify", "--verbose", apk)
run(bt / "aapt", "dump", "badging", apk)
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
(dist / (apk.name + ".sha256")).write_text(digest + "  " + apk.name + "\n", encoding="utf-8")
print("APK:", apk)
print("SHA256:", digest)
