"""資格を使わない Windows 中継経路確認を既存モデル境界に追加する。"""

import json
import re
import time

from scoped_provider_bridge import ProviderStore
from scoped_r2_bridge import Denied


class NonceProviderStore(ProviderStore):
    """公開 nonce の一致だけを返し、既存の Responses 制約は委譲して維持する。"""

    def route(self, method, target, headers, body):
        """期限内の単一 GET にだけ応答し、認証情報付き疎通試験を拒否する。"""
        if target.startswith("/__codex_review_nonce/"):
            values = {name.lower(): value for name, value in headers.items()}
            nonce = self.config["reviewNonce"]
            assert re.fullmatch("[a-f0-9]{32}", nonce)
            forbidden = {"authorization", "proxy-authorization", "cookie", "x-api-key"}
            if (
                method != "GET"
                or target != "/__codex_review_nonce/" + nonce
                or body
                or forbidden.intersection(values)
                or time.monotonic() >= self.until
            ):
                raise Denied("NONCE_SCOPE")
            result = json.dumps({"schema_version": 1, "nonce": nonce}).encode("utf-8")
            return 200, {"Content-Type": "application/json"}, result

        # モデル・スキーマ・認証・回数の境界は検証済み実装へ渡す。
        return super().route(method, target, headers, body)
