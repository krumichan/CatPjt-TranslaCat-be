"""Validate Docker --env-file input without printing values or contacting services."""
from __future__ import annotations

import base64
import binascii
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit


REQUIRED = (
    "DB_URL DB_USERNAME DB_PASSWORD JWT_SECRET_KEY GOOGLE_CLIENT_ID GEMINI_API_KEY "
    "GOOGLE_PROXY_URL FRONTEND_URL AI_SERVER_URL AI_SERVER_API_KEY LL_INTERNAL_SERVER_URL "
    "LL_INTERNAL_JWT_SECRET_BASE64 CHAT_GATEWAY_BASE_URL "
    "CHAT_RUNTIME_ENVIRONMENT CHAT_GATEWAY_SECRET_BASE64 "
    "CHAT_CORE_IDENTITY_SECRET_BASE64 CLOUDFLARE_R2_BUCKET CLOUDFLARE_R2_ACCESS_KEY_ID "
    "CLOUDFLARE_R2_SECRET_ACCESS_KEY CLOUDFLARE_R2_ENDPOINT CLOUDFLARE_R2_REGION "
    "CLOUDFLARE_R2_PUBLIC_BASE_URL NOVEL_AI_SERVER_API_KEY NOVEL_AUDIO_SPOOL_DIR"
).split()
EXPECTED = {
    "AI_SERVER_URL": "https://161.33.34.50:8443",
    "LL_INTERNAL_SERVER_URL": "https://150.230.61.24:8443",
    "CHAT_GATEWAY_BASE_URL": "https://217.142.244.88:8443",
    "CHAT_RUNTIME_ENVIRONMENT": "Production",
    "FRONTEND_URL": "https://translacat.vercel.app",
    "NOVEL_AUDIO_SPOOL_DIR": "/app/data/novel-audio-spool",
}
RETIRED_AVAILABILITY_KEYS = (
    "NOVEL_READER_ENABLED", "NOVEL_AI_ENABLED", "NOVEL_GATEWAY_ENABLED",
    "CHAT_GATEWAY_ENABLED", "CHAT_CORE_IDENTITY_ENABLED",
    "LANGUAGE_LEARNING_REMOTE_ENABLED", "LANGUAGE_LEARNING_GROWTH_ENABLED",
    "TRANSLACAT_VOICE_ENABLED",
)


def validate(raw: bytes) -> list[str]:
    errors: list[str] = []

    # Docker는 값의 인용부호/$/추가 '='를 해석하지 않는다. 값을 임의로 strip하지 않는다.
    if raw.startswith(b"\xef\xbb\xbf"):
        errors.append("FILE: UTF-8 BOM is not allowed")
    if b"\r" in raw or b"\0" in raw:
        errors.append("FILE: use LF without NUL bytes")
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        return errors + ["FILE: UTF-8 decoding failed"]
    values: dict[str, str] = {}
    for number, line in enumerate(text.splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
            errors.append(f"LINE {number}: invalid KEY=value record")
            continue
        if key in values:
            errors.append(f"{key}: duplicate key")
        values[key] = value

    # 현재 prod profile의 필수 계약을 검사하되 credential 값은 어느 오류에도 넣지 않는다.
    for key in REQUIRED:
        value = values.get(key, "")
        if not value.strip():
            errors.append(f"{key}: missing or empty")
        elif re.search(r"(?i)(?:change[_-]?me|replace[_-]?me|placeholder|<[^>]+>|\$\{)", value):
            errors.append(f"{key}: unresolved placeholder")
    for key, expected in EXPECTED.items():
        if values.get(key) != expected:
            errors.append(f"{key}: production contract mismatch")
    for key in RETIRED_AVAILABILITY_KEYS:
        if key in values:
            errors.append(f"{key}: retired service availability setting must be removed")

    # 소설 전용 인증은 필수이며 기존 공통 AI 소비자의 키로 대체하지 않는다.
    novel_key = values.get("NOVEL_AI_SERVER_API_KEY", "")
    if len(novel_key.encode("utf-8")) < 32:
        errors.append("NOVEL_AI_SERVER_API_KEY: requires at least 32 bytes")
    if novel_key and novel_key == values.get("AI_SERVER_API_KEY"):
        errors.append("NOVEL_AI_SERVER_API_KEY: must differ from the shared AI key")
    for key in ("JWT_SECRET_KEY", "LL_INTERNAL_JWT_SECRET_BASE64", "CHAT_GATEWAY_SECRET_BASE64", "CHAT_CORE_IDENTITY_SECRET_BASE64"):
        try:
            decoded = base64.b64decode(values.get(key, ""), validate=True)
            if len(decoded) < 32:
                errors.append(f"{key}: requires at least 256 bits")
        except (binascii.Error, ValueError):
            errors.append(f"{key}: invalid Base64")

    # URL query는 application-prod.properties가 붙인다. Core DB 이전/로컬 설정 혼입을 차단한다.
    db = values.get("DB_URL", "")
    if not re.fullmatch(r"jdbc:(?:log4jdbc:)?mysql://[a-zA-Z0-9.-]+\.tidbcloud\.com:4000/translacat", db):
        errors.append("DB_URL: expected the existing TiDB Core URL without a query")
    for key in ("GOOGLE_PROXY_URL", "CLOUDFLARE_R2_ENDPOINT", "CLOUDFLARE_R2_PUBLIC_BASE_URL"):
        try:
            parsed = urlsplit(values.get(key, ""))
        except ValueError:
            errors.append(f"{key}: invalid URL")
            continue
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
            errors.append(f"{key}: expected an HTTPS URL without userinfo")
        elif parsed.hostname in ("localhost", "127.0.0.1", "::1", "host.docker.internal"):
            errors.append(f"{key}: local host is forbidden")
    return errors


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 scripts/validate-release-env.py ENV_FILE")
    try:
        errors = validate(Path(sys.argv[1]).read_bytes())
    except OSError:
        raise SystemExit("Environment file cannot be read") from None
    if errors:
        print("\n".join(errors), file=sys.stderr)
        raise SystemExit(2)
    print("Production env validation PASS (values are not printed)")


if __name__ == "__main__":
    main()
