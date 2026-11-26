"""既存 GitHub Secret 内で設定を読み、値を出力せず運用準備の状態だけを返す。"""

import base64
import binascii
from datetime import datetime, timezone
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import ssl
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, HTTPSHandler, Request, build_opener

BUCKET = 'translacat-private-assets'
TOKEN_NAMES = ('CLOUDFLARE_API_TOKEN', 'CF_API_TOKEN', 'CLOUDFLARE_R2_API_TOKEN')


class NoRedirects(HTTPRedirectHandler):
    """認証ヘッダーを別の場所へ転送しない。"""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        """Cloudflare の固定 GET からの redirect を拒否する。"""
        return None


def parse_bundle(raw):
    """既存 deploy.yaml と同じ env-file 形式を読む。入力原文は変更・保存しない。"""
    if not raw.strip() or raw.startswith('\ufeff') or '\r' in raw or '\0' in raw:
        raise ValueError('BUNDLE_FORMAT')
    values = {}
    for line in raw.split('\n'):
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        name, separator, value = line.partition('=')
        if not separator or re.fullmatch(r'[A-Z][A-Z0-9_]*', name) is None or name in values:
            raise ValueError('BUNDLE_RECORD')
        values[name] = value
    return values


def key_status(value, camera=False):
    """鍵の存在・形式・版を示すハッシュだけを出力可能な結果へ変換する。"""
    report = {'present': bool(value), 'valid': False}
    if not value:
        return report
    if camera:
        try:
            report['valid'] = len(base64.b64decode(value, validate=True)) == 32
        except (ValueError, binascii.Error):
            pass
    else:
        report['valid'] = len(value.encode('utf-8')) >= 32
    if report['valid']:
        report['valueFingerprintSha256'] = sha256(value.encode('utf-8')).hexdigest()
    return report


def management_get(account, token, suffix):
    """同じ既存バケットの管理 GET を一回だけ実行し、公開ドメイン名や応答本文を出さない。"""
    assert suffix in ('managed', 'custom')
    url = f'https://api.cloudflare.com/client/v4/accounts/{account}/r2/buckets/{BUCKET}/domains/{suffix}'
    request = Request(url, method='GET', headers={'Authorization': 'Bearer ' + token,
                                                 'Accept': 'application/json'})
    opener = build_opener(NoRedirects(), HTTPSHandler(context=ssl.create_default_context()))
    try:
        with opener.open(request, timeout=20) as response:
            status = response.status
            raw = response.read(256_001)
        if status != 200 or len(raw) > 256_000:
            return {'httpStatus': status, 'verified': False}
        body = json.loads(raw)
        if body.get('success') is not True:
            return {'httpStatus': status, 'verified': False}
        if suffix == 'managed':
            enabled = body['result']['enabled']
            if not isinstance(enabled, bool):
                raise ValueError('SHAPE')
            return {'httpStatus': status, 'verified': True, 'enabled': enabled}
        domains = body['result']['domains']
        if not isinstance(domains, list) or any(not isinstance(item.get('enabled'), bool) for item in domains):
            raise ValueError('SHAPE')
        return {'httpStatus': status, 'verified': True, 'enabledCount': sum(item['enabled'] for item in domains)}
    except HTTPError as failure:
        return {'httpStatus': failure.code, 'verified': False}
    except Exception as failure:
        return {'httpStatus': None, 'verified': False, 'failureType': type(failure).__name__}


def main():
    """既存 bundle の内部だけで検査し、必要な認証がある時だけ固定二 GET を読む。"""
    report = {'atUtc': datetime.now(timezone.utc).isoformat(), 'readOnly': True,
              'operatingWrites': 0, 'objectRequests': 0, 'bucketPolicyChanges': 0,
              'secretValuesExported': False, 'secretVersionsChanged': False}
    try:
        values = parse_bundle(os.environ.get('ENV_FILE_CONTENT', ''))
    except Exception:
        report['status'] = 'EXISTING_BUNDLE_INVALID'
    else:
        report['status'] = 'EXISTING_BUNDLE_READ'
        report['cameraOcrKey'] = key_status(values.get('CAMERA_TRANSLATION_OCR_KEY', ''), camera=True)
        report['imageAssetSecret'] = key_status(values.get('TRANSLACAT_IMAGE_TRANSLATION_ASSET_SECRET', ''))
        report['imagePrivateBucketConfigured'] = values.get('TRANSLACAT_IMAGE_TRANSLATION_PRIVATE_BUCKET') == BUCKET
        report['boardBucketMatchesCandidate'] = values.get('CLOUDFLARE_R2_BOARD_BUCKET') == BUCKET
        report['frontendOriginMatches'] = values.get('FRONTEND_URL') == 'https://translacat.vercel.app'
        present = [name for name in TOKEN_NAMES if values.get(name)]
        report['managementTokenNamesPresent'] = present
        report['managementRequests'] = 0
        endpoint = values.get('CLOUDFLARE_R2_ENDPOINT', '')
        match = re.fullmatch(r'https://([a-f0-9]{32})(?:\.(?:eu|fedramp))?\.r2\.cloudflarestorage\.com/?', endpoint)
        if present and match and report['boardBucketMatchesCandidate']:
            token = values[present[0]]
            report['managedDomain'] = management_get(match.group(1), token, 'managed')
            report['customDomains'] = management_get(match.group(1), token, 'custom')
            report['managementRequests'] = 2
            report['privateSettingsVerified'] = (report['managedDomain'].get('verified') is True
                and report['managedDomain'].get('enabled') is False
                and report['customDomains'].get('verified') is True
                and report['customDomains'].get('enabledCount') == 0)
        else:
            report['privateSettingsVerified'] = False
            report['managementReadUnavailable'] = 'NO_EXISTING_MANAGEMENT_TOKEN_OR_APPROVED_TARGET_CONTEXT'
    output = Path('configuration-readiness.json')
    output.write_text(json.dumps(report, sort_keys=True, indent=2), encoding='utf-8')
    print(json.dumps(report, sort_keys=True))


if __name__ == '__main__':
    main()
