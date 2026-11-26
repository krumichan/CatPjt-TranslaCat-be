"""合成入力の文字だけに対して応答するローカル QA 用モデル代替。"""
from __future__ import annotations

import hmac
import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MODEL = "translacat-mac-fixture-v2"
EXECUTE = "/internal/v1/model/execute"
MAX_BODY = 500_000
JAPANESE_RUN = re.compile(r"[\u3040-\u30ff\u3400-\u9fff\uf900-\ufaff]+")
REPLACEMENTS = {"予約受付": "Reservation intake", "休憩室": "break room"}


def fixture_text(source: str) -> str:
    """日本語部分を決定的な英字に置き換え、他言語・符号・改行を維持する。"""
    if source in REPLACEMENTS:
        return REPLACEMENTS[source]
    return JAPANESE_RUN.sub(lambda match: "Test" if len(match[0]) < 4 else "Sample", source)


def execution_result(command: dict) -> dict:
    """文書コマンドの各 ID と配列の順序を維持した応答を返す。"""
    if command["taskName"] != "DOCUMENT_TRANSLATION_V1" or command["schemaName"] != "document_translation_v1":
        raise ValueError("QA_DOCUMENT_COMMAND")
    messages = command["messages"]
    if len(messages) != 1 or messages[0]["role"] != "user":
        raise ValueError("QA_DOCUMENT_MESSAGES")
    source = json.loads(messages[0]["content"])
    if source["sourceLanguage"] != "ja" or source["targetLanguage"] != "en":
        raise ValueError("QA_DOCUMENT_LANGUAGES")

    # ID は BE から受け取った文字列だけに限定し、内容をログやデータに保存しない。
    segments = []
    for segment in source["segments"]:
        pieces = [
            {"pieceId": piece["pieceId"], "text": fixture_text(piece["text"])}
            for piece in segment["pieces"]
        ]
        segments.append({"segmentId": segment["segmentId"], "pieces": pieces})
    return {"model": MODEL, "provider": "local-fixture", "providerCalls": 1, "output": {"segments": segments}}


class TranslationHandler(BaseHTTPRequestHandler):
    """隔離ネットワークのヘルスと合成コマンドだけを処理する。"""

    def log_message(self, format, *args):
        """HTTP の本文や入力パスをランタイムログに残さない。"""

    # エラー詳細に受信入力を含めず、常に Content-Length を確定する。
    def _send(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        """固定のヘルスパスに QA 代替モードを返す。"""
        if self.path == "/__qa/health":
            self._send(200, {"provider": "local-fixture", "model": MODEL})
        else:
            self._send(404, {"code": "QA_NOT_FOUND"})

    def do_POST(self):
        """認証済み文書コマンドにだけ有限の合成結果を返す。"""
        if self.path != EXECUTE:
            self._send(404, {"code": "QA_NOT_FOUND"})
            return
        key = self.headers.get("X-API-KEY", "")
        if not key.isascii() or not hmac.compare_digest(key, self.server.qa_token):
            self._send(403, {"code": "QA_TOKEN"})
            return

        try:
            length = int(self.headers.get("Content-Length", ""))
            if not 0 < length <= MAX_BODY:
                raise ValueError("QA_INPUT_SIZE")
            command = json.loads(self.rfile.read(length))
            result = execution_result(command)
        except (KeyError, ValueError, IndexError, TypeError, OverflowError):
            self._send(422, {"code": "QA_DOCUMENT_COMMAND"})
            return
        self._send(200, result)


def main() -> None:
    """合成キーを受け取り、実行者が隔離したポートだけを起動する。"""
    token = os.environ["TRANSLACAT_QA_FIXTURE_TOKEN"]
    if not re.fullmatch(r"[A-Za-z0-9_-]{24,96}", token):
        raise ValueError("QA_FIXTURE_TOKEN")
    bind = os.environ.get("TRANSLACAT_QA_FIXTURE_BIND", "127.0.0.1")
    if bind not in {"127.0.0.1", "0.0.0.0"}:
        raise ValueError("QA_FIXTURE_BIND")
    with ThreadingHTTPServer((bind, 27182), TranslationHandler) as server:
        server.qa_token = token
        server.serve_forever()


if __name__ == "__main__":
    main()
