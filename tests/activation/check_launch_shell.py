"""Exercise the generated launch command with a harmless fake app_process under an actual POSIX shell."""
from pathlib import Path
import os
import re
import shutil
import subprocess


def verify(java: Path, classes: Path):
    bash = shutil.which('bash') or shutil.which('sh')
    if bash is None and Path('C:/Program Files/Git/bin/bash.exe').is_file():
        bash = 'C:/Program Files/Git/bin/bash.exe'
    if bash is None:
        raise RuntimeError('A POSIX shell is required for the actual bootstrap-command check (Git Bash on Windows).')
    folder = classes / 'launch-shell'
    folder.mkdir(exist_ok=True)
    fake = folder / 'fake-app-process.sh'
    fake.write_text(
        "#!/bin/sh\n"
        "printf 'ACT|FAKE|ENTERED\\n'\n"
        "printf 'APK=%s\\n' \"$CLASSPATH\"\n"
        "for arg in \"$@\"; do printf 'ARG=%s\\n' \"$arg\"; done\n"
        "kill -HUP $$\n"
        "printf 'ACT|FAKE|HUP_SURVIVED\\n'\n"
        "printf 'ACT|FAKE|VISIBLE_FAILURE\\n' >&2\n"
        "exit 7\n", encoding='utf-8', newline='\n')
    fake.chmod(0o700)
    hostile = "/data/app/a' `touch x` $(touch y)/base.apk"
    fixture = subprocess.run([str(java), '-cp', str(classes),
                              'org.nfcresearch.qpolllab.activation.ActivationCommandFixture', hostile],
                             check=True, capture_output=True, text=True)
    command = fixture.stdout
    assert command.count('/system/bin/app_process') == 1
    # Replace only the executable under test; all quoting, env, traps, args, redirection and wait are production.
    command = command.replace('/system/bin/app_process', './fake-app-process.sh', 1)
    env = dict(os.environ, MSYS2_ARG_CONV_EXCL='*', MSYS_NO_PATHCONV='1')
    result = subprocess.run([bash, '-c', command], cwd=folder, env=env,
                            capture_output=True, text=True, timeout=15)
    assert result.returncode == 7, 'wait did not preserve child exit status'
    lines = result.stdout.splitlines()
    assert re.search(r'^ACT\|SHELL_STARTED\|[0-9]+$', result.stdout, re.MULTILINE)
    assert 'ACT|FAKE|ENTERED' in lines and 'ACT|FAKE|HUP_SURVIVED' in lines
    assert 'ACT|FAKE|VISIBLE_FAILURE' in lines, 'stderr was suppressed before handoff'
    assert 'APK=' + hostile in lines, 'CLASSPATH shell quoting changed the installed path'
    expected_args = ['/system/bin', '--nice-name=suixinyin-internal-10329',
                     'org.nfcresearch.qpolllab.activation.InternalBridgeBootstrap', '10329',
                     '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', hostile]
    assert [line[4:] for line in lines if line.startswith('ARG=')] == expected_args
    assert not (folder / 'x').exists() and not (folder / 'y').exists(), 'APK path executed shell substitutions'
    print('PASS actual launch shell: inherited HUP ignore, exact argv/CLASSPATH quoting, visible stderr and child wait/exit', flush=True)
    print('This fake app_process check does not execute Android, Binder, SELinux or NFC.', flush=True)
