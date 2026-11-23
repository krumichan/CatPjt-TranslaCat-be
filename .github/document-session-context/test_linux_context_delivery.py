"""固定 Linux 役割ファイルを、合成資格と自分の UUID だけで検証する。"""

import base64
import contextlib
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import stat
import time
import unittest
from unittest.mock import patch
import uuid

import linux_context_delivery as target
import temporary_session

ROOT = Path(__file__).resolve().parent
PRIVATE = Path('/root/.codex-private/document-r2')


class LinuxContextTests(unittest.TestCase):
    """実ストレージ・モデル・運用キーを一切使用しない POSIX 境界テスト。"""

    def setUp(self):
        """新しい合成 UUID と、固定検証器で署名した偽セッションを作る。"""
        self.run = uuid.uuid4()
        material = temporary_session.make_session(
            'https://' + 'a' * 32 + '.r2.cloudflarestorage.com', 'FAKEACCESS1234567890', 'p' * 40, self.run.hex,
        )
        client = {'endpoint': 'http://127.0.0.1:26991', 'access_key': 'TCATDOC' + 'A' * 24, 'secret_key': 'a' * 64}
        self.rows = {
            '-r2-session.json': {'schema_version': 1, 'run_id': str(self.run), 'client': client, 'upstream': material},
            '-openai-session.json': {
                'schema_version': 1, 'run_id': str(self.run),
                'expires_at': datetime.fromtimestamp(material['expiresAt'], timezone.utc).isoformat().replace('+00:00', 'Z'),
                'openai': {'base_url': 'http://127.0.0.1:26992/v1', 'bearer_token': 'b' * 64},
            },
        }
        self.packet = {'mode': 'deliver', 'run_id': str(self.run), 'files': {},
                       'validator_source': base64.b64encode((ROOT / 'temporary_session.py').read_bytes()).decode()}
        for suffix in target.SUFFIXES:
            self.update(suffix)

    def update(self, suffix):
        """同じ合成値の生バイトと正確なハッシュを更新する。"""
        body = json.dumps(self.rows[suffix]).encode()
        self.packet['files'][suffix] = {'body_base64': base64.b64encode(body).decode(),
                                       'sha256': hashlib.sha256(body).hexdigest()}

    def invoke(self, mode='deliver'):
        """JSON 出力を捕捉し、資格の値をテストログに出さない。"""
        packet = dict(self.packet, mode=mode)
        stream = io.StringIO()
        with contextlib.redirect_stdout(stream):
            target.main(packet)
        return json.loads(stream.getvalue())

    def paths(self):
        """同じ UUID の最終名と next だけを列挙する。"""
        return [PRIVATE / (str(self.run) + suffix + ending)
                for suffix in target.SUFFIXES for ending in ('', '.next')]

    def absent(self):
        """dangling link も含め、自分のファイルが残っていないことを確認する。"""
        self.assertTrue(all(not path.exists() and not path.is_symlink() for path in self.paths()))

    def tearDown(self):
        """正常ケースの同じハッシュだけを回収し、不一致を勝手に消さない。"""
        if PRIVATE.exists():
            self.invoke('cleanup')
        self.absent()

    def test_normal_delivery_and_cleanup(self):
        """二役を分離した 0700/0600 の配置と回収を実測する。"""
        result = self.invoke()
        self.assertEqual(result['changedFiles'], 2)
        self.assertEqual(stat.S_IMODE(PRIVATE.stat().st_mode), 0o700)
        for suffix in target.SUFFIXES:
            path = PRIVATE / (str(self.run) + suffix)
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertEqual(path.stat().st_nlink, 1)
        self.assertEqual(self.invoke('cleanup')['remainingFiles'], 0)
        self.absent()

    def test_second_role_failure_publishes_nothing(self):
        """二番目の役割が誤っていれば、一番目も公開しない。"""
        self.rows['-openai-session.json']['upstream'] = {'synthetic': True}
        self.update('-openai-session.json')
        with self.assertRaises(AssertionError):
            self.invoke()
        self.absent()

    def test_wrong_run_scope_rejected(self):
        """別 UUID の署名範囲をこの実行に渡さない。"""
        self.rows['-r2-session.json']['upstream'] = temporary_session.make_session(
            'https://' + 'a' * 32 + '.r2.cloudflarestorage.com', 'FAKEACCESS1234567890', 'p' * 40, uuid.uuid4().hex,
        )
        self.update('-r2-session.json')
        with self.assertRaises(ValueError):
            self.invoke()
        self.absent()

    def test_expired_session_rejected(self):
        """期限切れの合成セッションを一つも公開しない。"""
        self.rows['-r2-session.json']['upstream'] = temporary_session.make_session(
            'https://' + 'a' * 32 + '.r2.cloudflarestorage.com', 'FAKEACCESS1234567890', 'p' * 40,
            self.run.hex, now=int(time.time()) - 1000,
        )
        self.update('-r2-session.json')
        with self.assertRaises(ValueError):
            self.invoke()
        self.absent()

    def test_wrong_hash_rejected(self):
        """異なる生バイトを受け入れない。"""
        self.packet['files']['-r2-session.json']['sha256'] = '0' * 64
        with self.assertRaises(AssertionError):
            self.invoke()
        self.absent()

    def test_hardlink_preserved_until_reconciled(self):
        """複数リンクを検出し、自分の検証リンクだけを外してから回収する。"""
        self.invoke()
        path = PRIVATE / (str(self.run) + '-r2-session.json')
        link = PRIVATE / (str(self.run) + '-synthetic-link')
        try:
            os.link(path, link)
            with self.assertRaises(AssertionError):
                self.invoke('cleanup')
            self.assertTrue(path.exists())
        finally:
            if link.exists():
                self.assertTrue(os.path.samefile(path, link))
                link.unlink()

    def test_different_contents_preserved(self):
        """不一致の内容を消さず、自分の元バイトを復元してから回収する。"""
        self.invoke()
        path = PRIVATE / (str(self.run) + '-r2-session.json')
        original = path.read_bytes()
        path.write_bytes(b'{}')
        try:
            with self.assertRaises(AssertionError):
                self.invoke('cleanup')
            self.assertEqual(path.read_bytes(), b'{}')
        finally:
            path.write_bytes(original)

    def test_publication_failure_cleans_next(self):
        """rename の合成失敗で next が残らない。"""
        with patch.object(target, 'publish', side_effect=OSError('synthetic-rename-failure')):
            with self.assertRaises(OSError):
                self.invoke()
        self.absent()

    def test_existing_context_not_replaced(self):
        """同じ UUID の二重配送は元の二ファイルを保存する。"""
        self.invoke()
        before = {path: path.read_bytes() for path in self.paths() if path.exists()}
        with self.assertRaises(AssertionError):
            self.invoke()
        self.assertEqual(before, {path: path.read_bytes() for path in before})


def main():
    """小さな結果と固定ソースの指紋だけを回収する。"""
    stream = io.StringIO()
    result = unittest.TextTestRunner(stream=stream, verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(LinuxContextTests))
    results = ROOT / 'results'
    results.mkdir(exist_ok=True)
    (results / 'linux-context-tests.log').write_text(stream.getvalue(), encoding='utf-8')
    record = {'passed': result.wasSuccessful(), 'tests': result.testsRun, 'failures': len(result.failures),
              'errors': len(result.errors), 'networkCalls': 0, 'productionCredentialsUsed': False,
              'sources': {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
                          for name in ('linux_context_delivery.py', 'temporary_session.py', 'test_linux_context_delivery.py')}}
    (results / 'linux-context-tests.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
    print(json.dumps(record))
    return 0 if result.wasSuccessful() else 1


if __name__ == '__main__':
    raise SystemExit(main())
