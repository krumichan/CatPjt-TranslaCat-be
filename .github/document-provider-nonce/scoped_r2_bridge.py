"""一時資格と実行 prefix だけを使い、本番 R2 資格を保持する検収用中継。"""

from datetime import datetime, timezone
import hashlib
import hmac
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import threading
import time
from urllib.parse import parse_qsl, quote, urlencode, urlsplit
from xml.etree import ElementTree

UUID = r'[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}'
KEY = re.compile(r'(?:source-' + UUID + '|result-' + UUID + '-' + UUID
                 + '|batch-' + UUID + '-' + UUID + '-[0-9]{1,4})')
MAX_BODY = 12 * 1024 * 1024
SAFE_ERROR = {'NoSuchUpload', 'NoSuchKey', 'AccessDenied', 'PreconditionFailed',
              'InvalidPart', 'EntityTooSmall', 'NoSuchBucket'}


def signature(secret, day, region, canonical):
    """既知の SigV4 scope で署名キーを導出する。"""
    key = ('AWS4' + secret).encode()
    for part in (day, region, 's3', 'aws4_request'):
        key = hmac.digest(key, part.encode(), 'sha256')
    return hmac.digest(key, canonical.encode(), 'sha256').hex()


def canonical_request(method, path, query, values, signed_names, digest):
    """HTTP 本文・固定パス・整列 query とヘッダーを署名対象にする。"""
    headers = ''.join(name + ':' + ' '.join(values[name].split()) + '\n' for name in signed_names)
    return '\n'.join((method, path, query, headers, ';'.join(signed_names), digest))


class Denied(Exception):
    """外部へ本文や資格を含めず、拒否を伝える。"""


class ScopedStore:
    """一実行の生成物だけを扱い、既存オブジェクトを照会しない。"""

    def __init__(self, config, credentials, journal, transport=None):
        """本番資格はメモリーに限り、一時資格と固定 prefix を検証する。"""
        assert re.fullmatch('[a-f0-9]{32}', config['runId'])
        assert config['bucket'] == 'translacat-private-assets'
        assert re.fullmatch('[A-Z0-9]{16,64}', config['temporaryAccess'])
        assert re.fullmatch('[a-f0-9]{64}', config['temporarySecret'])
        parsed = urlsplit(credentials['endpoint'])
        assert parsed.scheme == 'https' and re.fullmatch(
            r'[a-f0-9]{32}(?:\.(?:eu|fedramp))?\.r2\.cloudflarestorage\.com', parsed.hostname or '')
        assert parsed.path in ('', '/') and not any((parsed.query, parsed.fragment, parsed.port,
                                                    parsed.username, parsed.password))
        self.config, self.credentials, self.host = config, credentials, parsed.hostname
        self.prefix = 'codex-doc-product-qa/' + config['runId'] + '/'
        self.journal = Path(journal)
        self.lock = threading.RLock()
        self.objects = {}
        self.events = []
        self.until = time.monotonic() + 7200
        self.transport = transport or self.direct
        self.concurrent = 2

    def save(self):
        """本文・署名・秘密値を記録せず、既知のキーと ID を原子的に保存する。"""
        with self.lock:
            data = {'runId': self.config['runId'], 'prefix': self.prefix,
                    'bucket': self.config['bucket'], 'objects': self.objects, 'events': self.events}
            temp = self.journal.with_suffix('.next')
            descriptor = os.open(temp, os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
            with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
                json.dump(data, stream)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temp, self.journal)

    def authorize(self, method, target, headers, body):
        """期限内の一時 SigV4 署名と本文の同一性を確認する。"""
        if time.monotonic() > self.until or len(body) > MAX_BODY:
            raise Denied('BOUNDS')
        values = {name.lower(): value for name, value in headers.items()}
        match = re.fullmatch(
            r'AWS4-HMAC-SHA256 Credential=([A-Z0-9]+)/([0-9]{8})/auto/s3/aws4_request, '
            r'SignedHeaders=([a-z0-9;-]+), Signature=([a-f0-9]{64})', values.get('authorization', ''))
        if not match or match[1] != self.config['temporaryAccess']:
            raise Denied('AUTH')
        signed = match[3].split(';')
        if signed != sorted(set(signed)) or not {'host', 'x-amz-date'} <= set(signed):
            raise Denied('AUTH_HEADERS')
        if any(name not in values for name in signed):
            raise Denied('AUTH_HEADERS')
        try:
            stamp = datetime.strptime(values['x-amz-date'], '%Y%m%dT%H%M%SZ').replace(tzinfo=timezone.utc)
        except (ValueError, KeyError):
            raise Denied('AUTH_TIME') from None
        if stamp.strftime('%Y%m%d') != match[2] or abs((datetime.now(timezone.utc) - stamp).total_seconds()) > 300:
            raise Denied('AUTH_TIME')
        parsed = urlsplit(target)
        pairs = parse_qsl(parsed.query, keep_blank_values=True) if parsed.query else []
        if len(pairs) != len(dict(pairs)):
            raise Denied('QUERY_DUPLICATE')
        query = urlencode(sorted(pairs), quote_via=quote, safe='-_.~')
        actual = hashlib.sha256(body).hexdigest()
        digest = values.get('x-amz-content-sha256', actual)
        if digest not in (actual, 'UNSIGNED-PAYLOAD'):
            raise Denied('PAYLOAD')
        if method in ('POST', 'PUT') and digest != actual:
            raise Denied('UNSIGNED_WRITE')
        canonical = canonical_request(method, parsed.path, query, values, signed, digest)
        scope = match[2] + '/auto/s3/aws4_request'
        to_sign = '\n'.join(('AWS4-HMAC-SHA256', values['x-amz-date'], scope,
                             hashlib.sha256(canonical.encode()).hexdigest()))
        expected = signature(self.config['temporarySecret'], match[2], 'auto', to_sign)
        if not hmac.compare_digest(expected, match[4]):
            raise Denied('AUTH_SIGNATURE')
        return parsed, dict(pairs), values

    def route(self, method, target, headers, body):
        """署名後も文書キー・単一パート・所有 ID の境界で全操作を制限する。"""
        parsed, query, values = self.authorize(method, target, headers, body)
        sdk_operation = query.pop('x-id', None)
        prefix = '/' + self.config['bucket'] + '/'
        if parsed.scheme or parsed.netloc or parsed.fragment or not parsed.path.startswith(prefix):
            raise Denied('PATH')
        key = parsed.path[len(prefix):]
        if not KEY.fullmatch(key):
            raise Denied('DOCUMENT_KEY')
        operation = None
        if not query and method in ('HEAD', 'GET', 'DELETE'):
            operation = method.lower()
        elif method == 'POST' and query == {'uploads': ''}:
            operation = 'create'
        elif set(query) <= {'uploadId', 'partNumber', 'max-parts'} and query.get('uploadId'):
            if method == 'PUT' and set(query) == {'uploadId', 'partNumber'} and query['partNumber'] == '1':
                operation = 'part'
            elif method == 'POST' and set(query) == {'uploadId'} and values.get('if-none-match') == '*':
                operation = 'complete'
            elif method == 'DELETE' and set(query) == {'uploadId'}:
                operation = 'abort'
            elif method == 'GET' and set(query) == {'uploadId', 'max-parts'} and query['max-parts'] == '1':
                operation = 'list-parts'
        if operation is None or (operation in ('head', 'get', 'delete', 'abort', 'list-parts') and body):
            raise Denied('OPERATION')
        sdk_names = {'head': 'HeadObject', 'get': 'GetObject', 'delete': 'DeleteObject',
                     'create': 'CreateMultipartUpload', 'part': 'UploadPart',
                     'complete': 'CompleteMultipartUpload', 'abort': 'AbortMultipartUpload',
                     'list-parts': 'ListParts'}
        if sdk_operation is not None and sdk_operation != sdk_names[operation]:
            raise Denied('SDK_OPERATION')
        with self.lock:
            if len(self.events) >= 3000:
                raise Denied('REQUEST_LIMIT')
            if operation == 'create':
                if key in self.objects or len(self.objects) >= 512 or values.get('x-amz-meta-document-write-id') != key:
                    raise Denied('CREATE_SCOPE')
                self.objects[key] = {'uploadId': None, 'terminal': False, 'deleted': False}
                self.save()
            entry = self.objects.get(key)
            if operation in ('part', 'complete', 'abort', 'list-parts'):
                if not entry or query['uploadId'] != entry['uploadId']:
                    raise Denied('UPLOAD_OWNER')
            if operation in ('get', 'delete') and not entry:
                raise Denied('OBJECT_OWNER')
            if operation == 'delete' and not entry['terminal']:
                raise Denied('DELETE_BEFORE_TERMINATION')
        forwarded = {name: value for name, value in values.items()
                     if name in ('content-type', 'cache-control', 'if-none-match', 'x-amz-meta-document-write-id')
                     or name.startswith('x-amz-checksum-') or name == 'x-amz-sdk-checksum-algorithm'}
        path = '/' + self.config['bucket'] + '/' + self.prefix + key
        canonical_query = urlencode(sorted(query.items()), quote_via=quote, safe='-_.~')
        started = time.monotonic()
        with self.lock:
            event = {'key': key, 'operation': operation, 'status': None, 'error': 'PENDING'}
            self.events.append(event)
            self.save()
        try:
            status, response_headers, response_body = self.transport(method, path, canonical_query, forwarded, body)
        except Exception:
            with self.lock:
                event.update(error='UPSTREAM_OUTCOME_UNKNOWN', elapsedMs=round((time.monotonic() - started) * 1000))
                self.save()
            raise
        error = None
        try:
            xml = ElementTree.fromstring(response_body) if response_body.lstrip().startswith(b'<') else None
        except ElementTree.ParseError:
            with self.lock:
                event.update(error='INVALID_UPSTREAM_XML', status=status)
                self.save()
            raise ValueError('Unconfirmed upstream XML') from None
        if xml is not None and xml.tag.rsplit('}', 1)[-1] == 'Error':
            found = xml.findtext('{*}Code')
            error = found if found in SAFE_ERROR else 'S3_ERROR'
        if 200 <= status < 300 and error is None:
            try:
                self.validate_success(operation, key, response_headers, response_body, xml)
            except ValueError:
                with self.lock:
                    event.update(error='UNCONFIRMED_UPSTREAM_SUCCESS', status=status)
                    self.save()
                raise
        with self.lock:
            if operation == 'create' and status == 200 and error is None:
                upload = xml.findtext('{*}UploadId') if xml is not None else None
                if not upload or len(upload) > 4096:
                    raise Denied('CREATE_RESPONSE_UNKNOWN')
                self.objects[key]['uploadId'] = upload
            if operation == 'complete' and status == 200 and error is None:
                self.objects[key]['terminal'] = True
            if operation == 'part' and status == 200 and error is None:
                self.objects[key]['bytes'] = len(body)
                self.objects[key]['sha256'] = hashlib.sha256(body).hexdigest()
            if operation == 'list-parts' and status == 404 and error == 'NoSuchUpload':
                self.objects[key]['terminal'] = True
            if operation == 'delete' and status == 204:
                self.objects[key]['deleted'] = True
            event.update(status=status, error=error, elapsedMs=round((time.monotonic() - started) * 1000))
            self.save()
        return self.public_response(operation, key, status, response_headers, response_body, xml, error)

    def validate_success(self, operation, key, headers, body, xml):
        """必要な応答欄が揃うまで、HTTP 200 を確定した成功とみなさない。"""
        values = {name.lower(): value for name, value in headers.items()}
        if operation in ('create', 'complete'):
            expected = 'InitiateMultipartUploadResult' if operation == 'create' else 'CompleteMultipartUploadResult'
            if xml is None or xml.tag.rsplit('}', 1)[-1] != expected:
                raise ValueError('Unconfirmed multipart result')
            value = xml.findtext('{*}UploadId' if operation == 'create' else '{*}ETag')
            if not value or not value.strip() or len(value) > 4096:
                raise ValueError('Missing multipart result field')
        if operation == 'part' and not values.get('etag', '').strip():
            raise ValueError('Missing part ETag')
        if operation in ('head', 'get'):
            entry = self.objects.get(key)
            if not entry or values.get('x-amz-meta-document-write-id') != key:
                raise ValueError('Unexpected object ownership')
            try:
                length = int(values.get('content-length', '-1'))
            except ValueError:
                raise ValueError('Invalid object length') from None
            if length != entry.get('bytes'):
                raise ValueError('Unexpected object length')
            if operation == 'get' and (len(body) != length or hashlib.sha256(body).hexdigest() != entry.get('sha256')):
                raise ValueError('Unexpected object payload')

    def public_response(self, operation, key, status, headers, body, xml, error):
        """供給者の生エラー・署名情報を返さず、SDK が必要な項目だけを組み立てる。"""
        safe_names = {'content-type', 'content-length', 'etag', 'last-modified', 'cache-control',
                      'accept-ranges', 'x-amz-meta-document-write-id', 'x-amz-checksum-crc32',
                      'x-amz-checksum-crc32c', 'x-amz-checksum-crc64nvme',
                      'x-amz-checksum-sha1', 'x-amz-checksum-sha256', 'x-amz-checksum-type'}
        safe = {name: value for name, value in headers.items() if name.lower() in safe_names}
        if status >= 400 or error:
            root = ElementTree.Element('Error')
            ElementTree.SubElement(root, 'Code').text = error or 'UnknownUpstreamError'
            return status, {'Content-Type': 'application/xml'}, ElementTree.tostring(root)
        if operation in ('create', 'complete', 'list-parts'):
            root = ElementTree.Element({'create': 'InitiateMultipartUploadResult',
                                        'complete': 'CompleteMultipartUploadResult',
                                        'list-parts': 'ListPartsResult'}[operation])
            ElementTree.SubElement(root, 'Bucket').text = self.config['bucket']
            ElementTree.SubElement(root, 'Key').text = key
            tags = {'create': ('UploadId',), 'complete': ('ETag',),
                    'list-parts': ('UploadId', 'PartNumberMarker', 'NextPartNumberMarker', 'MaxParts', 'IsTruncated')}[operation]
            if xml is not None:
                for tag in tags:
                    value = xml.findtext('{*}' + tag)
                    if value is not None:
                        ElementTree.SubElement(root, tag).text = value
                if operation == 'list-parts':
                    for found in xml.findall('{*}Part'):
                        part = ElementTree.SubElement(root, 'Part')
                        for tag in ('PartNumber', 'LastModified', 'ETag', 'Size'):
                            value = found.findtext('{*}' + tag)
                            if value is not None:
                                ElementTree.SubElement(part, tag).text = value
            return status, {'Content-Type': 'application/xml'}, ElementTree.tostring(root)
        return status, safe, body

    def direct(self, method, path, query, headers, body):
        """固定された R2 endpoint にだけ本番資格で再署名して送る。"""
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        digest = hashlib.sha256(body).hexdigest()
        values = {**headers, 'host': self.host, 'x-amz-date': stamp, 'x-amz-content-sha256': digest}
        names = sorted(values)
        canonical = canonical_request(method, path, query, values, names, digest)
        scope = stamp[:8] + '/auto/s3/aws4_request'
        to_sign = '\n'.join(('AWS4-HMAC-SHA256', stamp, scope, hashlib.sha256(canonical.encode()).hexdigest()))
        signed = signature(self.credentials['secret'], stamp[:8], 'auto', to_sign)
        values['authorization'] = ('AWS4-HMAC-SHA256 Credential=' + self.credentials['access'] + '/' + scope
                                   + ', SignedHeaders=' + ';'.join(names) + ', Signature=' + signed)
        connection = http.client.HTTPSConnection(self.host, timeout=18)
        try:
            connection.request(method, path + ('?' + query if query else ''), body=body, headers=values)
            response = connection.getresponse()
            data = response.read(MAX_BODY + 1)
            if len(data) > MAX_BODY:
                raise Denied('RESPONSE_BOUND')
            return response.status, dict(response.getheaders()), data
        finally:
            connection.close()

    def cleanup_owned(self):
        """停止後の既知 ID と確認済み自己オブジェクトだけを片付ける。"""
        result = {'runId': self.config['runId'], 'keys': [], 'complete': True}
        for key, entry in self.objects.items():
            row = {'key': key, 'state': 'pending'}
            result['keys'].append(row)
            try:
                if not KEY.fullmatch(key) or not entry.get('uploadId'):
                    raise ValueError('Unknown upload requires reconciliation')
                path = '/' + self.config['bucket'] + '/' + self.prefix + key
                upload = urlencode({'uploadId': entry['uploadId']}, quote_via=quote, safe='-_.~')
                status, _, body = self.transport('DELETE', path, upload, {}, b'')
                error = ElementTree.fromstring(body).findtext('{*}Code') if body else None
                if status not in (200, 204) and not (status == 404 and error == 'NoSuchUpload'):
                    raise ValueError('Abort unconfirmed')
                query = urlencode(sorted({'uploadId': entry['uploadId'], 'max-parts': '1'}.items()), quote_via=quote, safe='-_.~')
                status, _, body = self.transport('GET', path, query, {}, b'')
                error = ElementTree.fromstring(body).findtext('{*}Code') if body else None
                if status != 404 or error != 'NoSuchUpload':
                    raise ValueError('Termination unconfirmed')
                entry['terminal'] = True
                status, headers, body = self.transport('HEAD', path, '', {}, b'')
                if status == 200:
                    self.validate_success('head', key, headers, body, None)
                    status, _, _ = self.transport('DELETE', path, '', {}, b'')
                    if status != 204:
                        raise ValueError('Delete unconfirmed')
                elif status != 404:
                    raise ValueError('Owned object status unconfirmed')
                status, _, _ = self.transport('HEAD', path, '', {}, b'')
                if status != 404:
                    raise ValueError('Final absence unconfirmed')
                entry['deleted'] = True
                row.update(state='settled', uploadTerminated=True, finalHeadStatus=404)
            except Exception:
                row['state'] = 'unresolved'
                result['complete'] = False
            self.save()
        return result


def make_handler(store):
    """ログを無効化した loopback HTTP handler を用意する。"""
    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'

        def log_message(self, *_args):
            """署名・URL・本文をアクセスログへ出さない。"""

        def dispatch(self):
            """長さを制限し、失敗は一般的な S3 エラーだけを返す。"""
            try:
                self.connection.settimeout(25)
                if self.headers.get('Transfer-Encoding'):
                    raise Denied('TRANSFER_ENCODING')
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 <= length <= MAX_BODY:
                    raise Denied('BODY_BOUND')
                body = self.rfile.read(length)
                if len(body) != length:
                    raise Denied('INCOMPLETE_BODY')
                status, headers, data = store.route(self.command, self.path, self.headers, body)
            except Denied:
                status, headers, data = 403, {'Content-Type': 'application/xml'}, b'<Error><Code>AccessDenied</Code></Error>'
            except Exception:
                status, headers, data = 502, {'Content-Type': 'application/xml'}, b'<Error><Code>ServiceUnavailable</Code></Error>'
            self.send_response(status)
            for name, value in headers.items():
                if name.lower() not in ('transfer-encoding', 'connection', 'content-length', 'server', 'date'):
                    self.send_header(name, value)
            declared = next((value for name, value in headers.items() if name.lower() == 'content-length'), '0')
            self.send_header('Content-Length', declared if self.command == 'HEAD' else str(len(data)))
            self.send_header('Connection', 'close')
            self.end_headers()
            if self.command != 'HEAD':
                try:
                    self.wfile.write(data)
                except (BrokenPipeError, ConnectionResetError):
                    pass
            self.close_connection = True

        do_GET = do_HEAD = do_POST = do_PUT = do_DELETE = dispatch

    return Handler


def serve_store(store, port, journal):
    """公開ポートを作らず、期限と専用 stop ファイルまで中継する。"""
    class LimitedServer(ThreadingHTTPServer):
        """同時本文数を四つに限定し、検収中のメモリー使用を制限する。"""

        slots = threading.BoundedSemaphore(getattr(store, 'concurrent', 4))

        def process_request(self, request, client_address):
            """空きがない接続を新しい worker を作らず閉じる。"""
            if not self.slots.acquire(blocking=False):
                self.shutdown_request(request)
                return
            try:
                super().process_request(request, client_address)
            except Exception:
                self.slots.release()
                raise

        def process_request_thread(self, request, client_address):
            """応答と接続終了の後に予約を返す。"""
            try:
                super().process_request_thread(request, client_address)
            finally:
                self.slots.release()

    server = LimitedServer(('127.0.0.1', port), make_handler(store))
    server.daemon_threads = False
    server.timeout = 1
    store.save()
    try:
        while time.monotonic() < store.until and not Path(journal).with_suffix('.stop').exists():
            server.handle_request()
    finally:
        server.server_close()
        store.save()


def serve(config, credentials, journal):
    """R2 用の固定スコープを loopback サーバーに接続する。"""
    serve_store(ScopedStore(config, credentials, journal), config['port'], journal)
