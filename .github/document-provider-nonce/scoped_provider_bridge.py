"""本番 API キーを保持し、文書検収の Responses 呼び出しだけを中継する。"""

import hmac
import http.client
import json
import os
from pathlib import Path
import re
import threading
import time

from scoped_r2_bridge import Denied, serve_store

MODEL = 'gpt-5.4-mini-2026-03-17'


class ProviderStore:
    """固定モデル・構造化文書応答・一時認証の境界でモデル使用を制限する。"""

    def __init__(self, config, credentials, journal, transport=None):
        """本番キーはメモリーに保持し、journal に本文を保存しない。"""
        assert re.fullmatch('[a-f0-9]{32}', config['runId'])
        assert re.fullmatch('[a-f0-9]{64}', config['temporaryBearer'])
        self.config, self.credentials = config, credentials
        self.journal = Path(journal)
        self.lock = threading.RLock()
        self.until = time.monotonic() + 7200
        self.events = []
        self.transport = transport or self.direct

    def save(self):
        """リクエスト内容と API キーを除いた回数・状態だけを永続化する。"""
        with self.lock:
            temp = self.journal.with_suffix('.next')
            descriptor = os.open(temp, os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
            with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
                json.dump({'runId': self.config['runId'], 'model': MODEL, 'events': self.events}, stream)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temp, self.journal)

    def route(self, method, target, headers, body):
        """単一の文書スキーマと合成実行の上限を満たす要求だけを送る。"""
        values = {name.lower(): value for name, value in headers.items()}
        if not hmac.compare_digest(values.get('authorization', ''), 'Bearer ' + self.config['temporaryBearer']):
            raise Denied('AUTH')
        if method != 'POST' or target != '/v1/responses' or len(body) > 512 * 1024 or time.monotonic() > self.until:
            raise Denied('BOUNDS')
        try:
            data = json.loads(body)
        except (ValueError, UnicodeDecodeError):
            raise Denied('JSON') from None
        allowed = {'model', 'instructions', 'input', 'reasoning', 'max_output_tokens', 'text', 'store'}
        if not isinstance(data, dict) or set(data) - allowed or data.get('model') != MODEL or data.get('store') is not False:
            raise Denied('MODEL_SCOPE')
        tokens = data.get('max_output_tokens')
        if type(tokens) is not int or not 1 <= tokens <= 12288 or data.get('reasoning') != {'effort': 'low'}:
            raise Denied('TOKEN_SCOPE')
        text = data.get('text')
        form = text.get('format') if isinstance(text, dict) else None
        if not isinstance(form, dict) or form.get('type') != 'json_schema' or form.get('name') != 'document_translation_v1' or form.get('strict') is not True:
            raise Denied('SCHEMA_SCOPE')
        if not isinstance(form.get('schema'), dict) or not isinstance(data.get('instructions'), str):
            raise Denied('SCHEMA_SCOPE')
        messages = data.get('input')
        if not isinstance(messages, list) or not 1 <= len(messages) <= 32:
            raise Denied('INPUT_SCOPE')
        if any(not isinstance(item, dict) or set(item) != {'role', 'content'}
               or item['role'] not in ('user', 'system', 'developer', 'assistant')
               or not isinstance(item['content'], str) for item in messages):
            raise Denied('INPUT_SCOPE')

        # 不明応答も一回として記録し、供給者 SDK の自動再送を追加しない。
        with self.lock:
            if len(self.events) >= 128:
                raise Denied('CALL_LIMIT')
            event = {'number': len(self.events) + 1, 'state': 'pending', 'status': None}
            self.events.append(event)
            self.save()
        started = time.monotonic()
        try:
            status, response_headers, response_body = self.transport(body)
        except Exception:
            with self.lock:
                event.update(state='outcome-unknown', elapsedMs=round((time.monotonic() - started) * 1000))
                self.save()
            raise
        with self.lock:
            event.update(state='returned', status=status, elapsedMs=round((time.monotonic() - started) * 1000))
            self.save()
        if status >= 400:
            # 生エラーに含まれる入力・プロジェクト識別子を検収クライアントへ返さない。
            code = None
            try:
                found = json.loads(response_body).get('error', {}).get('code')
                if found in ('rate_limit_exceeded', 'insufficient_quota', 'invalid_api_key',
                             'model_not_found', 'server_error'):
                    code = found
            except (ValueError, AttributeError):
                pass
            response_body = json.dumps({'error': {'message': 'Document provider request failed',
                                                 'type': 'upstream_error', 'code': code}}).encode()
        return status, {'Content-Type': 'application/json'}, response_body

    def direct(self, body):
        """固定された公式 API にだけ、サーバー内のキーで一回送る。"""
        headers = {'Authorization': 'Bearer ' + self.credentials['apiKey'], 'Content-Type': 'application/json'}
        for name, field in (('OpenAI-Organization', 'organization'), ('OpenAI-Project', 'project')):
            if self.credentials.get(field):
                headers[name] = self.credentials[field]
        connection = http.client.HTTPSConnection('api.openai.com', timeout=90)
        try:
            connection.request('POST', '/v1/responses', body=body, headers=headers)
            response = connection.getresponse()
            payload = response.read(1024 * 1024 + 1)
            if len(payload) > 1024 * 1024:
                raise Denied('RESPONSE_BOUND')
            return response.status, dict(response.getheaders()), payload
        finally:
            connection.close()


def serve(config, credentials, journal):
    """期限内の文書専用中継を固定 loopback ポートに接続する。"""
    serve_store(ProviderStore(config, credentials, journal), config['port'], journal)
