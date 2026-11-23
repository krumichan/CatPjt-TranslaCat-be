"""Windows 用の公開 nonce と既存 Responses 制約を外部呼出しなしで検証する。"""

from hashlib import sha256
import json
from pathlib import Path
import tempfile
import time
import unittest

from provider_nonce import NonceProviderStore
from scoped_r2_bridge import Denied

ROOT = Path(__file__).resolve().parent
RUN = "1234567890abcdef1234567890abcdef"
BEARER = "a" * 64
PATH = "/__codex_review_nonce/" + RUN


class NonceTests(unittest.TestCase):
    """合成本文、偽キー、mock transport のみで追加境界を確認する。"""

    def setUp(self):
        """外部へ接続しない輸送と、一意な合成 journal の場所を用意する。"""
        scratch = ROOT / "nonce-test-output"
        scratch.mkdir(exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=scratch)
        self.calls = []

        def transport(body):
            """モデル相当の呼出しを記録するだけでネットワークへ出ない。"""
            self.calls.append(body)
            return 200, {}, b'{"output":[]}'

        self.store = NonceProviderStore(
            {"runId": RUN, "temporaryBearer": BEARER},
            {"apiKey": "synthetic-not-a-real-key"},
            Path(self.temporary.name) / "journal.json",
            transport=transport,
        )

    def tearDown(self):
        """このテストが作った合成 journal だけを回収する。"""
        self.temporary.cleanup()

    def deny(self, method, path, headers=None, body=b""):
        """拒否時にモデル輸送も journal event も増えないことを確認する。"""
        with self.assertRaises(Denied):
            self.store.route(method, path, headers or {}, body)
        self.assertEqual([], self.calls)
        self.assertEqual([], self.store.events)

    def test_exact_nonce_get_without_credentials(self):
        """有効な公開 nonce だけを返し、モデル呼出しを消費しない。"""
        status, headers, body = self.store.route("GET", PATH, {"Host": "127.0.0.1:26992"}, b"")
        self.assertEqual(200, status)
        self.assertEqual("application/json", headers["Content-Type"])
        self.assertEqual({"schema_version": 1, "nonce": RUN}, json.loads(body))
        self.assertEqual([], self.calls)
        self.assertEqual([], self.store.events)

    def test_different_nonce_is_refused(self):
        """別の UUID がこのセッションを確認できないことを検証する。"""
        self.deny("GET", "/__codex_review_nonce/" + "b" * 32)

    def test_query_suffix_is_refused(self):
        """nonce の別表現を認めず、固定パス契約を保つ。"""
        self.deny("GET", PATH + "?extra=1")

    def test_post_nonce_is_refused(self):
        """公開疎通は GET 一種類に限定する。"""
        self.deny("POST", PATH)

    def test_nonce_body_is_refused(self):
        """疎通経路に任意の本文を通さない。"""
        self.deny("GET", PATH, body=b"unexpected")

    def test_credential_headers_are_refused(self):
        """通常キーも短期 bearer も送らない契約を確認する。"""
        for name in ("Authorization", "Proxy-Authorization", "Cookie", "X-Api-Key"):
            with self.subTest(header=name):
                self.deny("GET", PATH, {name: "synthetic"})

    def test_expired_nonce_is_refused(self):
        """既存の短期 service deadline を nonce にも適用する。"""
        self.store.until = time.monotonic() - 1
        self.deny("GET", PATH)

    def test_responses_scope_still_delegates(self):
        """認証済み固定文書契約だけが既存輸送へ一度渡ることを検証する。"""
        data = {
            "model": "gpt-5.4-mini-2026-03-17",
            "instructions": "Synthetic document only",
            "input": [{"role": "user", "content": "Synthetic text"}],
            "reasoning": {"effort": "low"},
            "max_output_tokens": 32,
            "text": {"format": {"type": "json_schema", "name": "document_translation_v1",
                                "strict": True, "schema": {"type": "object"}}},
            "store": False,
        }
        raw = json.dumps(data).encode()
        status, _, _ = self.store.route(
            "POST", "/v1/responses", {"Authorization": "Bearer " + BEARER}, raw
        )
        self.assertEqual(200, status)
        self.assertEqual([raw], self.calls)
        self.assertEqual(1, len(self.store.events))

    def test_unrelated_route_remains_unauthorized(self):
        """nonce を追加しても元の認証境界を迂回できない。"""
        self.deny("GET", "/v1/responses")


if __name__ == "__main__":
    result = unittest.TextTestRunner(verbosity=2).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(NonceTests)
    )
    report = {
        "passed": result.wasSuccessful(),
        "tests": result.testsRun,
        "failures": len(result.failures),
        "errors": len(result.errors),
        "networkCalls": 0,
        "productionCredentialsUsed": False,
        "sources": {name: sha256((ROOT / name).read_bytes()).hexdigest()
                    for name in ("provider_nonce.py", "test_provider_nonce.py")},
    }
    (ROOT / "provider-nonce-tests.json").write_text(
        json.dumps(report, indent=2), encoding="utf-8"
    )
    raise SystemExit(0 if result.wasSuccessful() else 1)
