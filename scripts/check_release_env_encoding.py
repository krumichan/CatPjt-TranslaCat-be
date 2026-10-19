"""Run the actual workflow encoder with synthetic input only; no external commands or secrets."""
from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / '.github' / 'workflows' / 'deploy.yaml'
KEY = 'MOBILE_AUTH_APPROVAL_SECRET'
REDIRECT = 'AUTH_MOBILE_ALLOWED_REDIRECT_URIS'
CALLBACK = 'translacat-dev://auth/callback'
SYNTHETIC_KEY = 'synthetic-only-approval-key-2026-' + 'k' * 32
MARKER = b'EXISTING_MARKER=preserved\n'
DRIVER = '''import json, os, sys
payload = json.load(sys.stdin)
os.environ = payload['environment']
exec(compile(payload['source'], '<workflow-release-env-encoding>', 'exec'), {'__name__': '__main__'})
'''


def require(condition: bool, case: str, reason: str) -> None:
    # 실패 원문에도 fixture 값이나 미래의 환경값을 출력하지 않는다.
    if not condition:
        raise AssertionError(f'{case}: {reason}')


def workflow_encoder() -> str:
    # PyYAML 없이 고정 step의 실제 run 블록만 추출한다. 병합 알고리즘을 테스트에 복제하지 않는다.
    source = WORKFLOW.read_bytes().decode('utf-8')
    lines = source.splitlines()
    name = '      - name: Encode the environment as data'
    starts = [index for index, line in enumerate(lines) if line == name]
    require(len(starts) == 1, 'workflow', 'encoder step must be unique')
    start = starts[0]
    end = next((index for index in range(start + 1, len(lines))
                if lines[index].startswith('      - name:')), len(lines))
    step = lines[start:end]
    require('        shell: python' in step, 'workflow', 'encoder must execute Python')
    for name in ('ENV_FILE_CONTENT', KEY, REDIRECT):
        expected = '          ' + name + ': ${{ secrets.' + name + ' }}'
        require(expected in step, 'workflow', 'dedicated Secret input missing')
    require('        run: python -B scripts/check_release_env_encoding.py' in lines[:start],
            'workflow', 'verify job must run this regression before release')
    runs = [index for index, line in enumerate(step) if line == '        run: |']
    require(len(runs) == 1, 'workflow', 'Python run block must be unique')
    body = []
    for line in step[runs[0] + 1:]:
        if line and not line.startswith('          '):
            break
        body.append(line[10:] if line else '')
    program = '\n'.join(body) + '\n'
    require('${{' not in program, 'workflow', 'Secret expressions must not be interpolated into Python source')
    compile(program, '<workflow-release-env-encoding>', 'exec')
    return program


def probe(source: str, original: str, overrides: dict[str, str], work_dir: Path | None):
    # child에는 합성 매핑만 준다. NUL도 OS 환경 대신 격리된 Python dict에서 검증한다.
    with tempfile.TemporaryDirectory(prefix='release-env-encoding-', dir=work_dir) as temporary:
        fixture = Path(temporary)
        destination = fixture / 'github-env'
        destination.write_bytes(MARKER)
        values = {'ENV_FILE_CONTENT': original, 'GITHUB_ENV': str(destination), **overrides}
        process_environment = {'TMP': str(fixture), 'TEMP': str(fixture)}
        for name in ('SystemRoot', 'WINDIR'):
            if name in os.environ:
                process_environment[name] = os.environ[name]
        payload = json.dumps({'environment': values, 'source': source}, ensure_ascii=True).encode('utf-8')

        # 실행: 고정된 로컬 Python 표준 라이브러리만 사용하며 Docker/Git/SSH/네트워크는 호출하지 않는다.
        result = subprocess.run([sys.executable, '-I', '-S', '-B', '-X', 'utf8', '-c', DRIVER],
                                input=payload, capture_output=True, cwd=fixture,
                                env=process_environment, timeout=10)
        return result, destination.read_bytes()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path)
    parser.add_argument('--report', type=Path)
    options = parser.parse_args()
    if options.work_dir:
        options.work_dir.mkdir(parents=True, exist_ok=True)
    source = workflow_encoder()
    results = []

    def accepted(name: str, original: str, overrides: dict[str, str], expected: str) -> None:
        # 검증: 실제 Base64 전달값과 마스킹 명령을 비교하고 raw 자격이 출력되지 않았는지 확인한다.
        process, output = probe(source, original, overrides, options.work_dir)
        encoded = base64.b64encode(expected.encode('utf-8'))
        require(process.returncode == 0, name, 'encoder failed')
        require(output == MARKER + b'DEPLOY_ENV_B64=' + encoded + b'\n', name, 'encoded payload changed')
        actual_encoded = output[len(MARKER + b'DEPLOY_ENV_B64='):-1]
        require(base64.b64decode(actual_encoded, validate=True) == expected.encode('utf-8'), name, 'payload bytes changed')
        require(process.stdout.replace(b'\r\n', b'\n') == b'::add-mask::' + encoded + b'\n',
                name, 'unexpected stdout or missing mask')
        require(process.stderr == b'', name, 'unexpected stderr')
        require(SYNTHETIC_KEY.encode() not in process.stdout + process.stderr, name, 'raw approval key was logged')
        results.append({'case': name, 'status': 'PASS'})

    def rejected(name: str, original: str, overrides: dict[str, str], code: str) -> None:
        # 검증: 모든 입력 거부는 출력 env를 건드리기 전에 고정 오류로 끝나야 한다.
        process, output = probe(source, original, overrides, options.work_dir)
        require(process.returncode != 0, name, 'invalid input was accepted')
        require(output == MARKER, name, 'failure changed GITHUB_ENV')
        require(process.stdout == b'', name, 'failure wrote stdout')
        expected = ('Environment encoding rejected: ' + code + '\n').encode()
        require(process.stderr.replace(b'\r\n', b'\n') == expected,
                name, 'failure disclosed input or returned an unstable error')
        results.append({'case': name, 'status': 'PASS'})

    # 빈 입력과 기존 값: 다른 값·주석·공백·구분자·마지막 LF를 재직렬화하지 않는다.
    unusual = '# ' + KEY + '=comment-only\n\nPASSWORD=  literal=$\'"+/\\=tail  \nOTHER=한글\n'
    existing = 'A=preserve\n' + KEY + '=old-literal\n' + REDIRECT + '=' + CALLBACK + '\n'
    accepted('unset-overrides-preserve-all-bytes', unusual, {}, unusual)
    accepted('empty-overrides-keep-existing-values', existing, {KEY: '', REDIRECT: ''}, existing)
    accepted('empty-overrides-preserve-no-final-lf', unusual[:-1], {KEY: '', REDIRECT: ''}, unusual[:-1])
    accepted('replace-exact-targets-only', existing, {KEY: SYNTHETIC_KEY, REDIRECT: CALLBACK},
             'A=preserve\n' + KEY + '=' + SYNTHETIC_KEY + '\n' + REDIRECT + '=' + CALLBACK + '\n')
    accepted('add-both-with-final-lf', unusual, {KEY: SYNTHETIC_KEY, REDIRECT: CALLBACK},
             unusual + KEY + '=' + SYNTHETIC_KEY + '\n' + REDIRECT + '=' + CALLBACK + '\n')
    accepted('add-both-without-final-lf', 'A=1', {KEY: SYNTHETIC_KEY, REDIRECT: CALLBACK},
             'A=1\n' + KEY + '=' + SYNTHETIC_KEY + '\n' + REDIRECT + '=' + CALLBACK)
    accepted('replace-without-final-lf', KEY + '=old', {KEY: SYNTHETIC_KEY}, KEY + '=' + SYNTHETIC_KEY)
    accepted('only-key-override-keeps-old-redirect', existing, {KEY: SYNTHETIC_KEY},
             'A=preserve\n' + KEY + '=' + SYNTHETIC_KEY + '\n' + REDIRECT + '=' + CALLBACK + '\n')
    accepted('only-redirect-override-keeps-old-key', KEY + '=old\n', {REDIRECT: CALLBACK},
             KEY + '=old\n' + REDIRECT + '=' + CALLBACK + '\n')
    accepted('target-name-inside-value-is-not-a-record', 'A=x' + KEY + '=inside\n', {KEY: SYNTHETIC_KEY},
             'A=x' + KEY + '=inside\n' + KEY + '=' + SYNTHETIC_KEY + '\n')
    # 이 fixture는 원문 보존만 검사한다. remote validator의 splitlines 문법에서는 유효 입력이 아니다.
    accepted('other-values-with-unicode-line-separators-are-preserved', 'A=one\u2028two\vthree\fend\n',
             {KEY: SYNTHETIC_KEY}, 'A=one\u2028two\vthree\fend\n' + KEY + '=' + SYNTHETIC_KEY + '\n')
    literal_key = 'synthetic-literal-$\'"=+/\\' + 'x' * 32
    accepted('new-key-is-literal-not-shell-or-base64', 'A=1\n', {KEY: literal_key},
             'A=1\n' + KEY + '=' + literal_key + '\n')
    accepted('minimum-utf8-bytes-not-character-count', 'A=1\n', {KEY: '가' * 11},
             'A=1\n' + KEY + '=' + '가' * 11 + '\n')
    accepted('exact-thirty-two-byte-key', 'A=1\n', {KEY: 'k' * 32}, 'A=1\n' + KEY + '=' + 'k' * 32 + '\n')

    # 잘못된 원본은 기존 validator처럼 거부하며 중복을 제거하는 방식으로 복구하지 않는다.
    rejected('empty-original', '', {KEY: SYNTHETIC_KEY}, 'ENV_FILE_CONTENT_EMPTY')
    rejected('whitespace-original', ' \n\t', {}, 'ENV_FILE_CONTENT_EMPTY')
    rejected('crlf-original', existing.replace('\n', '\r\n'), {KEY: SYNTHETIC_KEY}, 'INVALID_ENV_FILE_ENCODING')
    rejected('crlf-original-with-empty-overrides', existing.replace('\n', '\r\n'), {}, 'INVALID_ENV_FILE_ENCODING')
    rejected('bare-cr-original', 'A=one\rtwo\n', {}, 'INVALID_ENV_FILE_ENCODING')
    rejected('bom-original', '\ufeffA=1\n', {}, 'INVALID_ENV_FILE_ENCODING')
    rejected('nul-original', 'A=one\0two\n', {}, 'INVALID_ENV_FILE_ENCODING')
    rejected('malformed-original-record', 'not-a-record\n', {}, 'INVALID_ENV_FILE_RECORD')
    rejected('whitespace-original-key', ' ' + KEY + '=old\n', {KEY: SYNTHETIC_KEY}, 'INVALID_ENV_FILE_RECORD')
    rejected('duplicate-target-before-replacement', KEY + '=old1\n' + KEY + '=old2\n',
             {KEY: SYNTHETIC_KEY}, 'DUPLICATE_ENV_FILE_KEY')
    rejected('duplicate-target-with-empty-override', existing + KEY + '=again\n', {}, 'DUPLICATE_ENV_FILE_KEY')
    rejected('duplicate-other-key', 'A=1\nA=2\n', {KEY: SYNTHETIC_KEY}, 'DUPLICATE_ENV_FILE_KEY')

    # 별도 입력은 공백/제어문자를 trim하지 않고 거부한다. 오류에는 값이 포함되지 않는다.
    rejected('short-key', 'A=1\n', {KEY: 'k' * 31}, 'APPROVAL_SECRET_TOO_SHORT')
    for label, value in (
            ('space-only', ' ' * 40), ('leading-space', ' ' + SYNTHETIC_KEY),
            ('trailing-space', SYNTHETIC_KEY + ' '), ('newline', SYNTHETIC_KEY + '\nINJECT=1'),
            ('carriage-return', SYNTHETIC_KEY + '\r'), ('nul', SYNTHETIC_KEY + '\0'),
            ('tab', SYNTHETIC_KEY + '\t'), ('del', SYNTHETIC_KEY + '\x7f'),
            ('unicode-space', SYNTHETIC_KEY + '\u00a0'), ('unicode-control', SYNTHETIC_KEY + '\u200b')):
        rejected('key-' + label, 'A=1\n', {KEY: value}, 'INVALID_MOBILE_OVERRIDE')
    for label, value in (
            ('query', CALLBACK + '?next=x'), ('fragment', CALLBACK + '#x'),
            ('userinfo', 'translacat-dev://user@auth/callback'), ('production-scheme', 'translacat://auth/callback'),
            ('duplicate-csv', CALLBACK + ',' + CALLBACK), ('unknown-scheme', 'other://auth/callback'),
            ('unicode', CALLBACK + '가')):
        rejected('redirect-' + label, 'A=1\n', {REDIRECT: value}, 'INVALID_DEVELOPMENT_CALLBACK')
    for label, value in (('space', CALLBACK + ' '), ('space-only', ' ' * 10),
                         ('newline', CALLBACK + '\nA=1'), ('nul', CALLBACK + '\0')):
        rejected('redirect-' + label, 'A=1\n', {REDIRECT: value}, 'INVALID_MOBILE_OVERRIDE')

    # 결과에는 합성 fixture의 내용도 저장하지 않고 시나리오 이름과 성공 여부만 남긴다.
    report = {'status': 'PASS', 'tests': len(results), 'failures': 0, 'skipped': 0,
              'workflow': '.github/workflows/deploy.yaml', 'actualInlinePythonExecuted': True,
              'externalOperations': False, 'cases': results}
    if options.report:
        options.report.parent.mkdir(parents=True, exist_ok=True)
        options.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"{len(results)}/{len(results)} workflow environment encoding scenarios PASS; synthetic input only.")


if __name__ == '__main__':
    main()
