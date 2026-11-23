"""所有コンテナの tmpfs に二つの短期役割ファイルを原子配置・回収する。"""

import base64
import ctypes
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import time
import uuid

VALIDATOR_SHA = '9d575e9ed14f07172fd03f41e49464123ab4fb9c99cf7da6877e242f72e5356d'
SUFFIXES = ('-r2-session.json', '-openai-session.json')


def identity(path):
    """同じ uid・単一リンク・0600 の通常ファイルだけを識別する。"""
    info = path.lstat()
    assert stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid()
    assert stat.S_IMODE(info.st_mode) == 0o600 and info.st_nlink == 1
    return info.st_dev, info.st_ino


def publish(source, target):
    """Linux の NOREPLACE rename で既存ファイルを置換せずに公開する。"""
    library = ctypes.CDLL('libc.so.6', use_errno=True)
    rename = library.renameat2
    rename.argtypes = (ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint)
    rename.restype = ctypes.c_int
    if rename(-100, os.fsencode(source), -100, os.fsencode(target), 1):
        raise OSError(ctypes.get_errno(), 'Owned context publication refused')


def validate_role(suffix, body, run, validator):
    """R2 とモデル認証を混ぜず、既存の固定 session 検証器を再利用する。"""
    row = json.loads(body)
    assert row['schema_version'] == 1 and row['run_id'] == str(run)
    if suffix == '-r2-session.json':
        assert set(row) == {'schema_version', 'run_id', 'client', 'upstream'}
        assert set(row['client']) == {'endpoint', 'access_key', 'secret_key'}
        assert row['client']['endpoint'] == 'http://127.0.0.1:26991'
        assert re.fullmatch('TCATDOC[A-F0-9]{24}', row['client']['access_key'])
        assert re.fullmatch('[a-f0-9]{64}', row['client']['secret_key'])
        validator['validate_material'](row['upstream'], run.hex)
        assert row['upstream']['expiresAt'] - time.time() >= 240
    else:
        assert set(row) == {'schema_version', 'run_id', 'expires_at', 'openai'}
        assert set(row['openai']) == {'base_url', 'bearer_token'}
        assert row['openai']['base_url'] == 'http://127.0.0.1:26992/v1'
        assert re.fullmatch('[a-f0-9]{64}', row['openai']['bearer_token'])
        expiry = datetime.fromisoformat(row['expires_at'].replace('Z', '+00:00'))
        assert expiry.tzinfo is not None
        assert 240 <= (expiry - datetime.now(timezone.utc)).total_seconds() <= 905


def main(packet):
    """資格を返さず、所有ファイルの配置数・残存数だけを出力する。"""
    assert sys.platform == 'linux' and os.geteuid() == 0
    assert packet['mode'] in ('deliver', 'cleanup')
    run = uuid.UUID(packet['run_id'])
    assert str(run) == packet['run_id'] and set(packet['files']) == set(SUFFIXES)
    root = Path('/root/.codex-private/document-r2')
    assert root.resolve() == root
    if packet['mode'] == 'deliver':
        root.mkdir(mode=0o700, parents=True, exist_ok=True)
    directory = root.lstat()
    assert stat.S_ISDIR(directory.st_mode) and directory.st_uid == os.getuid()
    assert stat.S_IMODE(directory.st_mode) == 0o700
    validator = {}
    if packet['mode'] == 'deliver':
        module = base64.b64decode(packet['validator_source'], validate=True)
        assert hashlib.sha256(module).hexdigest() == VALIDATOR_SHA
        exec(compile(module, 'fixed-session-validator', 'exec'), validator)
    prepared = {}
    if packet['mode'] == 'deliver':
        for suffix in SUFFIXES:
            item = packet['files'][suffix]
            body = base64.b64decode(item['body_base64'], validate=True)
            assert 0 < len(body) <= 16384 and hashlib.sha256(body).hexdigest() == item['sha256']
            validate_role(suffix, body, run, validator)
            prepared[suffix] = body
    changed = 0
    for suffix in SUFFIXES:
        item = packet['files'][suffix]
        assert re.fullmatch('[a-f0-9]{64}', item['sha256'])
        target = root / (str(run) + suffix)
        temporary = target.with_suffix(target.suffix + '.next')
        if packet['mode'] == 'cleanup':
            for path in (target, temporary):
                if path.exists() or path.is_symlink():
                    identity(path)
                    assert hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256']
                    path.unlink()
                    changed += 1
            continue
        body = prepared[suffix]
        assert not target.exists() and not target.is_symlink()
        descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        created = os.fstat(descriptor)
        try:
            with os.fdopen(descriptor, 'wb') as output:
                output.write(body)
                output.flush()
                os.fsync(output.fileno())
            publish(temporary, target)
            changed += 1
        finally:
            if temporary.exists() or temporary.is_symlink():
                assert identity(temporary) == (created.st_dev, created.st_ino)
                temporary.unlink()
    remaining = sum((root / (str(run) + suffix + ending)).exists()
                    for suffix in SUFFIXES for ending in ('', '.next'))
    if packet['mode'] == 'cleanup':
        assert remaining == 0
    print(json.dumps({'mode': packet['mode'], 'changedFiles': changed,
                      'remainingFiles': remaining, 'credentialsReturned': False}))


if __name__ == '__main__':
    try:
        main(json.load(sys.stdin))
    except Exception as failure:
        print(json.dumps({'failed': True, 'errorType': type(failure).__name__, 'credentialsReturned': False}))
        raise SystemExit(1)
