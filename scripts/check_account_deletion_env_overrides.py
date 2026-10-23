"""Execute the actual Actions flag-merge step with synthetic inputs only."""
from __future__ import annotations

import argparse
import base64
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / '.github/workflows/deploy.yaml'
NAMES = ('ACCOUNT_DELETION_ENABLED', 'ACCOUNT_DELETION_WORKER_ENABLED')
MARKER = b'EXISTING_MARKER=preserved\n'
DRIVER = '''import json, os, sys
payload = json.load(sys.stdin)
os.environ = payload['environment']
exec(compile(payload['source'], '<workflow-account-deletion-merge>', 'exec'), {'__name__': '__main__'})
'''


def require(condition: bool, case: str, reason: str) -> None:
    if not condition:
        raise AssertionError(case + ': ' + reason)


def workflow_program() -> str:
    # 실제 checkout 없는 Actions run 블록을 추출한다. 병합 구현을 테스트에 복제하지 않는다.
    lines = WORKFLOW.read_text(encoding='utf-8').splitlines()
    marker = '      - name: Merge public account deletion flags'
    starts = [index for index, line in enumerate(lines) if line == marker]
    require(len(starts) == 1, 'workflow', 'merge step must be unique')
    start = starts[0]
    encoder = lines.index('      - name: Encode the environment as data')
    ssh = lines.index('      - name: Prepare strict BE SSH')
    require(encoder < start < ssh, 'workflow', 'merge must follow encoder and precede SSH')
    end = next(index for index in range(start + 1, len(lines)) if lines[index].startswith('      - name:'))
    step = lines[start:end]
    require('        shell: python' in step, 'workflow', 'merge must execute Python')
    require('        run: python -B scripts/check_account_deletion_env_overrides.py' in lines[:encoder],
            'workflow', 'verify must test the actual merge before release')
    for name in NAMES:
        require('          ' + name + ': ${{ vars.' + name + ' }}' in step,
                'workflow', 'public Variable must enter through step env')
    runs = [index for index, line in enumerate(step) if line == '        run: |']
    require(len(runs) == 1, 'workflow', 'run block must be unique')
    body = []
    for line in step[runs[0] + 1:]:
        if line and not line.startswith('          '):
            break
        body.append(line[10:] if line else '')
    program = '\n'.join(body) + '\n'
    require('${{' not in program, 'workflow', 'expressions must not enter executable source')
    compile(program, '<workflow-account-deletion-merge>', 'exec')
    return program


def probe(source: str, original: str | bytes, overrides: dict[str, str], work_dir: Path | None):
    # 준비: 실제 환경/Secret 대신 완전히 격리한 합성 매핑만 자식 Python에 전달한다.
    with tempfile.TemporaryDirectory(prefix='account-deletion-env-', dir=work_dir) as temporary:
        folder = Path(temporary)
        destination = folder / 'github-env'
        destination.write_bytes(MARKER)
        raw = original.encode('utf-8') if isinstance(original, str) else original
        environment = {'DEPLOY_ENV_B64': base64.b64encode(raw).decode('ascii'),
                       'GITHUB_ENV': str(destination), **overrides}
        process_environment = {'TMP': str(folder), 'TEMP': str(folder)}
        for name in ('SystemRoot', 'WINDIR'):
            if name in os.environ:
                process_environment[name] = os.environ[name]
        payload = json.dumps({'source': source, 'environment': environment}, ensure_ascii=True).encode()

        # 실행: 외부 명령/네트워크/서비스를 실행하지 않고 workflow Python만 실행한다.
        result = subprocess.run([sys.executable, '-I', '-S', '-B', '-X', 'utf8', '-c', DRIVER],
                                input=payload, capture_output=True, env=process_environment,
                                cwd=folder, timeout=10)
        return result, destination.read_bytes()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path)
    parser.add_argument('--report', type=Path)
    options = parser.parse_args()
    if options.work_dir:
        options.work_dir.mkdir(parents=True, exist_ok=True)
    source = workflow_program()
    results = []

    def accepted(case: str, original: str, overrides: dict[str, str], expected: str) -> None:
        # 검증: 다른 Secret 원문·구분자·마지막 LF를 보존하고 최종 Base64를 새로 마스킹한다.
        process, output = probe(source, original, overrides, options.work_dir)
        encoded = base64.b64encode(expected.encode('utf-8'))
        require(process.returncode == 0, case, 'merge failed')
        require(output == MARKER + b'DEPLOY_ENV_B64=' + encoded + b'\n', case, 'final env payload differs')
        require(process.stdout.replace(b'\r\n', b'\n') == b'::add-mask::' + encoded + b'\n', case, 'missing derived mask or unexpected stdout')
        require(process.stderr == b'', case, 'unexpected stderr')
        require(b'synthetic-secret-literal' not in process.stdout + process.stderr, case, 'raw secret exposed')
        for name in NAMES:
            require(sum(line.startswith(name + '=') for line in expected.split('\n')) == 1, case, 'flag is duplicated')
        results.append({'case': case, 'status': 'PASS'})

    def rejected(case: str, original: str | bytes, overrides: dict[str, str], code: str) -> None:
        # 검증: 잘못된 값과 중복은 출력 파일을 열기 전에 원문 없는 고정 오류로 거부한다.
        process, output = probe(source, original, overrides, options.work_dir)
        require(process.returncode != 0, case, 'invalid input accepted')
        require(output == MARKER, case, 'failure changed prior encoded env')
        require(process.stdout == b'', case, 'failure wrote stdout')
        expected = ('Account deletion environment merge rejected: ' + code + '\n').encode()
        require(process.stderr.replace(b'\r\n', b'\n') == expected, case, 'failure leaked input or changed error')
        results.append({'case': case, 'status': 'PASS'})

    enabled, worker = NAMES
    off = enabled + '=false\n' + worker + '=false\n'
    active = enabled + '=true\n' + worker + '=true\n'
    paused = enabled + '=false\n' + worker + '=true\n'
    other = '# flag names in comments stay literal\nSECRET=synthetic-secret-literal=$\'"+/\\=끝  \nOTHER=한글\n'
    accepted('initial-absent-is-false-false', other, {}, other + off)
    accepted('initial-empty-is-false-false', other, dict.fromkeys(NAMES, ''), other + off)
    accepted('initial-no-final-lf', 'A=1', {}, 'A=1\n' + off[:-1])
    accepted('existing-active-preserved-when-vars-absent', other + active, {}, other + active)
    accepted('existing-active-preserved-when-vars-empty', other + active, dict.fromkeys(NAMES, ''), other + active)
    accepted('existing-paused-worker-preserved', other + paused, {enabled: ''}, other + paused)
    accepted('explicit-activation-replaces-both-once', other + off, dict.fromkeys(NAMES, 'true'), other + active)
    accepted('stop-new-acceptance-keeps-existing-worker', other + active, {enabled: 'false'}, other + paused)
    accepted('resume-acceptance-keeps-existing-worker', other + paused, {enabled: 'true'}, other + active)
    accepted('only-worker-override-is-independent', other + active, {worker: 'false'}, other + enabled + '=true\n' + worker + '=false\n')
    accepted('partial-existing-worker-preserved', other + worker + '=true\n', {}, other + worker + '=true\n' + enabled + '=false\n')
    accepted('partial-existing-acceptance-preserved', other + enabled + '=true\n', {}, other + enabled + '=true\n' + worker + '=false\n')
    accepted('exact-key-only-comments-and-suffix-preserved', other + '# ' + enabled + '=true\n' + enabled + '_OTHER=literal\n',
             {enabled: 'true'}, other + '# ' + enabled + '=true\n' + enabled + '_OTHER=literal\n' + enabled + '=true\n' + worker + '=false\n')
    accepted('replace-preserves-no-final-lf', other + active[:-1], {enabled: 'false'}, other + paused[:-1])
    for acceptance in ('true', 'false'):
        for working in ('true', 'false'):
            accepted('independent-combination-' + acceptance + '-' + working, 'A=1\n',
                     {enabled: acceptance, worker: working}, 'A=1\n' + enabled + '=' + acceptance + '\n' + worker + '=' + working + '\n')

    # 원본 잘못된 값도 유효 override로 덮어 감추지 않으며, 두 입력을 trim/정규화하지 않는다.
    for name in NAMES:
        for index, invalid in enumerate(('TRUE', 'False', '1', '0', ' true', 'true ', ' ', 'true\nINJECT=1', 'false\r', 'true\0', 'false\t', 'ｔｒｕｅ')):
            rejected('invalid-public-' + name + '-' + str(index), other, {name: invalid}, 'INVALID_PUBLIC_FLAG')
            rejected('invalid-existing-' + name + '-' + str(index), other + name + '=' + invalid.replace('\n', '\v') + '\n',
                     {name: 'false'}, 'INVALID_EXISTING_FLAG' if '\r' not in invalid and '\0' not in invalid else 'INVALID_ENV_FILE_ENCODING')
        rejected('empty-existing-' + name, other + name + '=\n', {}, 'INVALID_EXISTING_FLAG')
        rejected('duplicate-existing-' + name, other + name + '=true\n' + name + '=false\n',
                 {name: 'true'}, 'DUPLICATE_ENV_FILE_KEY')
    rejected('duplicate-other-key', 'A=1\nA=2\n', {}, 'DUPLICATE_ENV_FILE_KEY')
    rejected('invalid-original-record', 'not-an-env-record\n', {}, 'INVALID_ENV_FILE_RECORD')
    rejected('empty-original', '', {}, 'ENV_FILE_CONTENT_EMPTY')
    rejected('crlf-original', 'A=1\r\n', {}, 'INVALID_ENV_FILE_ENCODING')
    rejected('bom-original', '\ufeffA=1\n', {}, 'INVALID_ENV_FILE_ENCODING')
    rejected('invalid-utf8-original', b'\xff', {}, 'INVALID_DEPLOY_ENV_ENCODING')
    rejected('invalid-base64-input', other, {'DEPLOY_ENV_B64': '!synthetic-invalid!'}, 'INVALID_DEPLOY_ENV_ENCODING')
    rejected('missing-output', other, {'GITHUB_ENV': ''}, 'GITHUB_ENV_MISSING')

    # 준비: 기존 encoder와 host validator를 연결한 전체 합성 환경이다. 실제 .env는 읽지 않는다.
    modules = {}
    for label, filename in (('encoding', 'check_release_env_encoding.py'), ('validator', 'validate-release-env.py')):
        spec = importlib.util.spec_from_file_location('flag_probe_' + label, ROOT / 'scripts' / filename)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        modules[label] = module
    validator = modules['validator']
    values = dict.fromkeys(validator.REQUIRED, 'test-only-value') | validator.EXPECTED
    values['NOVEL_AI_SERVER_API_KEY'] = 'novel-only-synthetic-authentication-key'
    for name in ('JWT_SECRET_KEY', 'LL_INTERNAL_JWT_SECRET_BASE64', 'CHAT_GATEWAY_SECRET_BASE64', 'CHAT_CORE_IDENTITY_SECRET_BASE64'):
        values[name] = base64.b64encode((name + 'x' * 64).encode()[:32]).decode()
    values['DB_URL'] = 'jdbc:log4jdbc:mysql://synthetic.tidbcloud.com:4000/translacat'
    for name in ('GOOGLE_PROXY_URL', 'CLOUDFLARE_R2_ENDPOINT', 'CLOUDFLARE_R2_PUBLIC_BASE_URL'):
        values[name] = 'https://synthetic.invalid'
    original = ''.join(name + '=' + value + '\n' for name, value in values.items())
    require(not validator.validate(original.encode()), 'chain', 'synthetic base environment is invalid')
    encoder = modules['encoding']
    old_program = encoder.workflow_encoder()

    # 실행/검증: 실제 이전 step의 Base64를 새 step에 주고 SSH 직전 validator 계약까지 확인한다.
    for acceptance, working in (('false', 'false'), ('true', 'true'), ('false', 'true'), ('true', 'false')):
        case = 'actual-encoder-merge-validator-chain-' + acceptance + '-' + working
        first, first_output = encoder.probe(old_program, original, {}, options.work_dir)
        require(first.returncode == 0 and first_output.startswith(encoder.MARKER + b'DEPLOY_ENV_B64='),
                case, 'upstream encoder failed')
        encoded = first_output[len(encoder.MARKER + b'DEPLOY_ENV_B64='):-1].decode('ascii')
        second, second_output = probe(source, '', {'DEPLOY_ENV_B64': encoded, enabled: acceptance, worker: working}, options.work_dir)
        require(second.returncode == 0 and second_output.startswith(MARKER + b'DEPLOY_ENV_B64='),
                case, 'merge failed after actual encoder')
        final_bytes = base64.b64decode(second_output[len(MARKER + b'DEPLOY_ENV_B64='):-1], validate=True)
        expected = original + enabled + '=' + acceptance + '\n' + worker + '=' + working + '\n'
        require(final_bytes == expected.encode(), case, 'chain changed original secret or flag bytes')
        require(not validator.validate(final_bytes), case, 'host environment validator rejected final payload')
        require(second.stdout.replace(b'\r\n', b'\n') == b'::add-mask::' + base64.b64encode(final_bytes) + b'\n',
                case, 'chain final mask missing')
        results.append({'case': case, 'status': 'PASS'})

    report = {'status': 'PASS', 'cases': len(results), 'results': results,
              'actualWorkflowStepExecuted': True, 'productionSecretsRead': False, 'networkRequests': 0}
    if options.report:
        options.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('PASS account deletion public flag overrides: ' + str(len(results)) + ' synthetic cases')


if __name__ == '__main__':
    main()
