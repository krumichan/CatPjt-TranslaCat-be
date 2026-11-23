"""公式 JWT 形式に従い、一実行 prefix に限る短期 R2 セッションを構成する。"""

import base64
import hashlib
import hmac
import json
import re
import time
from urllib.parse import urlsplit

BUCKET = 'translacat-private-assets'
ACTIONS = ('HeadObject', 'GetObject', 'DeleteObject', 'CreateMultipartUpload', 'UploadPart',
           'CompleteMultipartUpload', 'AbortMultipartUpload', 'ListParts')
HOST = re.compile(r'([a-f0-9]{32})(?:\.(?:eu|fedramp))?\.r2\.cloudflarestorage\.com')


def encode_url(data):
    """JWT 用の padding なし base64url を返す。"""
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode('ascii')


def unique_object(items):
    """重複した認証フィールドを拒否する。"""
    fields = dict(items)
    if len(fields) != len(items):
        raise ValueError('Duplicate session field')
    return fields


def claims_from_token(token):
    """秘密を必要とせずに宣言を読み取る。署名検証とは区別する。"""
    raw = base64.b64decode(token, validate=True)
    if len(raw) > 8192 or not raw.startswith(b'jwt/'):
        raise ValueError('Invalid session envelope')
    parts = raw[4:].split(b'.')
    if len(parts) != 3 or any(not re.fullmatch(rb'[A-Za-z0-9_-]+', item) for item in parts):
        raise ValueError('Invalid JWT encoding')
    decode = lambda item: base64.urlsafe_b64decode(item + b'=' * (-len(item) % 4))
    header = json.loads(decode(parts[0]), object_pairs_hook=unique_object)
    payload = json.loads(decode(parts[1]), object_pairs_hook=unique_object)
    if header != {'alg': 'HS256', 'typ': 'JWT'} or len(decode(parts[2])) != 32:
        raise ValueError('Invalid session algorithm')
    return raw[4:].decode('ascii'), payload


def validate_material(material, run_id, now=None):
    """期待するバケット・八操作・正確な prefix・短期期限だけを受け付ける。"""
    fields = {'schemaVersion', 'runId', 'bucket', 'endpoint', 'access', 'secret', 'sessionToken', 'expiresAt'}
    if not isinstance(material, dict) or set(material) != fields or material['schemaVersion'] != 1:
        raise ValueError('Unexpected session fields')
    if not re.fullmatch('[A-Za-z0-9]{16,128}', material['access']) or not re.fullmatch('[a-f0-9]{64}', material['secret']):
        raise ValueError('Unexpected session credential format')
    if not re.fullmatch('[a-f0-9]{32}', run_id) or material['runId'] != run_id:
        raise ValueError('Unexpected session run')
    endpoint = urlsplit(material['endpoint'])
    host = HOST.fullmatch(endpoint.hostname or '')
    if (not host or endpoint.scheme != 'https' or endpoint.path not in ('', '/')
            or any((endpoint.username, endpoint.password, endpoint.port, endpoint.query, endpoint.fragment))):
        raise ValueError('Unexpected session endpoint')
    jwt, claims = claims_from_token(material['sessionToken'])
    expected = {'bucket', 'actions', 'paths', 'sub', 'iss', 'aud', 'iat', 'exp'}
    if not isinstance(claims, dict) or set(claims) != expected:
        raise ValueError('Unexpected session claims')
    prefix = 'codex-doc-product-qa/' + run_id + '/'
    if claims['bucket'] != material['bucket'] or material['bucket'] != BUCKET:
        raise ValueError('Unexpected session bucket')
    if claims['actions'] != list(ACTIONS) or claims['paths'] != {'prefixPaths': [prefix], 'objectPaths': []}:
        raise ValueError('Unexpected session permissions')
    if claims['sub'] != host[1] or claims['aud'] != endpoint.hostname or claims['iss'] != material['access']:
        raise ValueError('Unexpected session issuer')
    clock = int(time.time()) if now is None else now
    if (type(claims['iat']) is not int or type(claims['exp']) is not int
            or not 60 <= claims['exp'] - claims['iat'] <= 900
            or claims['iat'] > clock + 30 or claims['exp'] <= clock
            or material['expiresAt'] != claims['exp']):
        raise ValueError('Expired or oversized session lifetime')
    derived = hashlib.sha256(jwt.encode('ascii')).hexdigest()
    if not hmac.compare_digest(derived, material['secret']):
        raise ValueError('Unexpected derived session key')
    return claims


def make_session(endpoint, access, parent_secret, run_id, *, now=None):
    """親 secret を戻り値へ入れず、固定 900 秒の一時署名を作る。"""
    host = HOST.fullmatch(urlsplit(endpoint).hostname or '')
    if not host or not re.fullmatch('[A-Za-z0-9]{16,128}', access):
        raise ValueError('Unexpected parent identity')
    if not re.fullmatch('[a-f0-9]{32}', run_id) or not isinstance(parent_secret, str) or len(parent_secret) < 32:
        raise ValueError('Unexpected signing input')
    issued = int(time.time()) if now is None else now
    claims = {'bucket': BUCKET, 'actions': list(ACTIONS),
              'paths': {'prefixPaths': ['codex-doc-product-qa/' + run_id + '/'], 'objectPaths': []},
              'sub': host[1], 'iss': access, 'aud': urlsplit(endpoint).hostname, 'iat': issued, 'exp': issued + 900}
    header = encode_url(json.dumps({'alg': 'HS256', 'typ': 'JWT'}, separators=(',', ':')).encode())
    payload = encode_url(json.dumps(claims, separators=(',', ':')).encode())
    unsigned = (header + '.' + payload).encode('ascii')
    jwt = unsigned + b'.' + encode_url(hmac.digest(parent_secret.encode('utf-8'), unsigned, 'sha256')).encode('ascii')
    material = {'schemaVersion': 1, 'runId': run_id, 'bucket': BUCKET, 'endpoint': endpoint,
                'access': access, 'secret': hashlib.sha256(jwt).hexdigest(),
                'sessionToken': base64.b64encode(b'jwt/' + jwt).decode('ascii'), 'expiresAt': issued + 900}
    validate_material(material, run_id, now=issued)
    return material
