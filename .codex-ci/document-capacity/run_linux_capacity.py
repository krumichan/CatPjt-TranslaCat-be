"""実製品 bootJar を隔離 MySQL/合成モデルに接続し、HTTP と cgroup を同時に検査する。"""

import argparse
import ast
from datetime import datetime, timezone
from hashlib import file_digest
import json
import os
import platform
from pathlib import Path
import shutil
import subprocess
import sys
import threading
import time
import uuid

import mac_runtime as previous
from linux_stack import LinuxStack
from linux_document_capacity_http import run_capacity_http

ROOT = Path(__file__).resolve().parent


def digest(path):
    """大きい製品 JAR もメモリに全体を載せずに SHA を取る。"""
    with path.open('rb') as source:
        return file_digest(source, 'sha256').hexdigest()


def wait_file(path, process, seconds):
    """同じ子プロセスの終了と期限を監視し、無限に待たない。"""
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if path.is_file():
            return
        if process.poll() is not None:
            raise RuntimeError('CAPACITY_SAMPLER_EXITED_EARLY')
        time.sleep(0.1)
    raise RuntimeError('CAPACITY_SAMPLER_READY_TIMEOUT')


def measure(args):
    """fresh fixture 一組だけを所有し、必ず ID/label を確認した片付けを行う。"""
    if sys.platform != 'linux' or not os.environ.get('GITHUB_RUN_ID', '').isdigit():
        raise RuntimeError('ISOLATED_LINUX_CI_REQUIRED')
    product = args.product_dir.resolve(strict=True)
    actual = subprocess.check_output(['git', '-c', 'safe.directory=' + str(product),
                                      '-C', str(product), 'rev-parse', 'HEAD']).decode().strip()
    if actual != args.product_sha:
        raise RuntimeError('PRODUCT_SOURCE_CHANGED')
    images = json.loads((ROOT / 'images.json').read_text())
    owner = os.environ['GITHUB_RUN_ID'] + '-' + os.environ['GITHUB_RUN_ATTEMPT']
    phase = Path(args.phase).resolve()
    if phase.exists() or not phase.is_relative_to(Path.cwd().resolve()):
        raise RuntimeError('FRESH_PHASE_DIRECTORY_REQUIRED')
    phase.mkdir(mode=0o755)
    evidence = phase / 'evidence'
    evidence.mkdir(mode=0o755)
    work = phase / 'owned-work'
    work.mkdir(mode=0o700)
    source = work / 'source'
    (source / 'be/src/main/resources').mkdir(parents=True)
    (source / 'server').mkdir()
    (source / 'be/scripts').symlink_to(product / 'scripts', target_is_directory=True)
    (source / 'be/src/main/resources/system_full.dic').symlink_to(product / 'src/main/resources/system_full.dic')
    shutil.copyfile(ROOT / 'bootstrap-be-password.java', source / 'server/bootstrap-be-password.java')
    jars = [p for p in (product / 'build/libs').glob('*.jar') if not p.name.endswith('-plain.jar')]
    if len(jars) != 1:
        raise RuntimeError('EXACT_BOOT_JAR_REQUIRED')
    jar = jars[0]
    required = {'20261019_auth_sessions.sql', '20261122_document_translation_jobs.sql',
                '20261122_document_translation_object_writes.sql', '20261123_document_translation_multipart_uploads.sql'}
    selected = tuple(name for name in previous.MYSQL_MIGRATIONS if (product / 'scripts/sql' / name).is_file())
    if not required <= set(selected):
        raise RuntimeError('ISOLATED_MIGRATION_INPUT_MISSING')
    previous.MYSQL_MIGRATIONS = selected
    stack = LinuxStack(work, uuid.uuid4(), images, owner, args.memory_mib)
    sampler = None
    jstat = None
    reader = None
    db_reader = None
    stop_db = threading.Event()
    db_states = {'samples': 0, 'errors': 0, 'targetIntervalMs': 100,
                 'maxQueued': 0, 'maxRunning': 0, 'maxReady': 0, 'maxActiveOrReady': 0,
                 'maxQueryElapsedMs': 0}
    heap = {'samples': 0, 'sampledPeakHeapUsedBytes': 0, 'jstatOverheadIncludedInCgroup': True}
    report = {'atUtc': datetime.now(timezone.utc).isoformat(), 'phase': args.phase,
              'productSha': args.product_sha, 'jarSha256': digest(jar), 'owner': owner,
              'memoryLimitMiB': args.memory_mib, 'passed': False, 'operatingRequests': 0,
              'paidProviderCalls': 0, 'productionCapacityProven': False, 'checkpoints': [],
              'kernelRelease': platform.release(), 'cgroupVersion': 2,
              'dockerServerVersion': subprocess.check_output(
                  ['docker', 'version', '--format', '{{.Server.Version}}'], timeout=10).decode().strip()}
    completed = evidence / 'completed.json'
    try:
        stack.start()
        stack.start_mysql(source)
        stack.start_provider(ROOT / 'fixture_provider.py')
        stack.prepare_be(source, jar)
        stack.start_be()
        stack.start_host_relay()
        stack.wait_be_ready()
        _login, token = stack.seed_account(Path(shutil.which('java')), source, jar, 'Owner')
        _other, other = stack.seed_account(Path(shutil.which('java')), source, jar, 'Other')
        report['containerId'] = stack.containers['be']
        sampler = subprocess.Popen([
            sys.executable, str(ROOT / 'sample_container.py'), '--container-id', stack.containers['be'],
            '--owner', owner, '--product-sha', args.product_sha,
            '--completed-file', str(completed), '--output', str(evidence / 'sampling.json')],
            cwd=phase, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        wait_file(evidence / 'sampler-ready.json', sampler, 20)

        # 同じ JVM の perf counters を標本化する。計測プロセスの費用も cgroup に含む。
        jstat = subprocess.Popen(['docker', 'exec', stack.containers['be'],
                                  '/opt/java/openjdk/bin/jstat', '-gc', '1', '100', '6000'],
                                 stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)

        def read_heap():
            """既知の heap 列の数値だけを残し、標本の生ログを保存しない。"""
            fields = None
            for line in jstat.stdout:
                values = line.split()
                if 'S0U' in values and 'OU' in values:
                    fields = values
                elif fields is not None and len(values) == len(fields):
                    try:
                        record = dict(zip(fields, values))
                        used = round(sum(float(record[key]) for key in ('S0U', 'S1U', 'EU', 'OU')) * 1024)
                    except (KeyError, ValueError, OverflowError):
                        continue
                    heap['samples'] += 1
                    heap['sampledPeakHeapUsedBytes'] = max(heap['sampledPeakHeapUsedBytes'], used)

        reader = threading.Thread(target=read_heap, daemon=True)
        reader.start()

        def read_db_states():
            """同じ合成 DB の状態件数だけを集計し、query の実所要時間も記録する。"""
            query = ("SELECT COALESCE(SUM(status='QUEUED'),0),"
                     "COALESCE(SUM(status IN ('VALIDATING','TRANSLATING','REPACKING','CANCELLING')),0),"
                     "COALESCE(SUM(status='SUCCEEDED' AND output_key IS NOT NULL),0) "
                     "FROM document_translation_job;")
            while not stop_db.is_set():
                began = time.monotonic()
                try:
                    queued, running, ready = map(int, stack.sql(query, timeout=5).stdout.strip().split(b'\t'))
                except (RuntimeError, OSError, ValueError, subprocess.SubprocessError):
                    db_states['errors'] += 1
                    return
                db_states['samples'] += 1
                db_states['maxQueued'] = max(db_states['maxQueued'], queued)
                db_states['maxRunning'] = max(db_states['maxRunning'], running)
                db_states['maxReady'] = max(db_states['maxReady'], ready)
                db_states['maxActiveOrReady'] = max(db_states['maxActiveOrReady'], queued + running + ready)
                elapsed = time.monotonic() - began
                db_states['maxQueryElapsedMs'] = max(db_states['maxQueryElapsedMs'], round(elapsed * 1000))
                stop_db.wait(max(0, 0.1 - elapsed))

        db_reader = threading.Thread(target=read_db_states, daemon=True)
        db_reader.start()
        started = time.monotonic()

        def checkpoint(label):
            """機能の固定段階と相対時刻だけを記録する。"""
            report['checkpoints'].append({'phase': label, 'elapsedMs': round((time.monotonic() - started) * 1000)})

        report['http'] = run_capacity_http(stack, port=previous.BE_PORT, input_dir=ROOT / 'fixtures',
                                           owner=token, other=other, checkpoint=checkpoint, isolated_fixture=True)
        report['harnessExitCode'] = 0
    except Exception as failure:
        report['harnessExitCode'] = 1
        report['errorType'] = type(failure).__name__
        code = str(failure)
        report['fixedFailure'] = code if code.isascii() and code.replace('_', '').isalnum() else 'ISOLATED_CAPACITY_FAILURE'
        report['dockerDiagnostic'] = stack.docker_diagnostic
        # 合成コンテナの終了状態だけを取得する。
        if stack.containers.get('be'):
            try:
                state = json.loads(stack.docker('inspect', '--format', '{{json .State}}',
                                                stack.containers['be']).stdout)
                report['backendExitState'] = {
                    'running': bool(state['Running']), 'oomKilled': bool(state['OOMKilled']),
                    'exitCode': int(state['ExitCode']),
                }
            except (RuntimeError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
                report['backendExitState'] = {'available': False}
        if 'be' in stack.containers:
            try:
                # 合成仕事の状態と NULL/zero フラグだけで失敗を分類する。本文や hash 値は読まない。
                rows = stack.sql("SELECT status,COALESCE(error_code,''),input_key IS NULL,"
                                 "output_key IS NULL,output_sha256 IS NULL,"
                                 "input_sha256=REPEAT('0',64) FROM document_translation_job "
                                 "ORDER BY created_at,id LIMIT 10;").stdout.decode().splitlines()
                report['failureDbFlags'] = [line.split('\t') for line in rows]
                names = stack.workspace_entries()
                report['failureWorkspace'] = {'entries': len(names),
                    'jobDirectories': len({name.split('/', 1)[0] for name in names if name.startswith('job-')}),
                    'sourceFiles': sum(name.endswith('/source') for name in names),
                    'resultFiles': sum(name.endswith('/result') for name in names)}
            except (RuntimeError, OSError, subprocess.SubprocessError):
                report['failureStateReadUnavailable'] = True
        if sampler and sampler.poll() is not None and (evidence / 'sampling.json').is_file():
            report['sampling'] = json.loads((evidence / 'sampling.json').read_text())
    finally:
        # 最終採取が終わるまで BE/cgroup を保持し、その後に自分の stack だけを削除する。
        stop_db.set()
        if db_reader:
            db_reader.join(timeout=6)
        report['databaseStates'] = db_states
        if sampler and sampler.poll() is None:
            temporary = evidence / 'completed.tmp'
            temporary.write_text(json.dumps({'owner': owner, 'productSha': args.product_sha,
                                              'exitCode': report.get('harnessExitCode', 1)}))
            temporary.replace(completed)
            try:
                sampler.wait(timeout=20)
            except subprocess.TimeoutExpired:
                sampler.terminate()
                sampler.wait(timeout=5)
        if (evidence / 'sampling.json').is_file():
            report['sampling'] = json.loads((evidence / 'sampling.json').read_text())
        report['cleanup'] = stack.close()
        if jstat:
            try:
                jstat.wait(timeout=5)
            except subprocess.TimeoutExpired:
                jstat.terminate()
                jstat.wait(timeout=5)
        if reader:
            reader.join(timeout=5)
        report['heap'] = heap
        report['passed'] = (report.get('harnessExitCode') == 0 and report.get('sampling', {}).get('passed')
                             and heap['samples'] > 0 and db_states['samples'] > 0
                             and db_states['errors'] == 0 and db_states['maxActiveOrReady'] <= 1
                             and report['cleanup']['owned_resources_removed'])
        report['migrationNames'] = selected
        report['images'] = images
        (evidence / 'capacity-result.json').write_text(json.dumps(report, indent=2))
        # 合成キーと設定も自分の phase 下の領域から回収する。
        if not work.resolve().is_relative_to(phase) or work.is_symlink():
            raise RuntimeError('OWNED_WORK_PATH_CHANGED')
        if report['cleanup']['owned_resources_removed']:
            shutil.rmtree(work)
        report['ownedWorkRemoved'] = not work.exists()
        (evidence / 'capacity-result.json').write_text(json.dumps(report, indent=2))
    print(json.dumps({'phase': args.phase, 'passed': report['passed'],
                      'fixedFailure': report.get('fixedFailure'), 'cleanup': report['cleanup']}))
    if not report['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--product-dir', type=Path, required=True)
    parser.add_argument('--product-sha', required=True)
    parser.add_argument('--memory-mib', type=int, choices=(2048, 768, 512), required=True)
    parser.add_argument('--phase', choices=('generous', 'bounded'), required=True)
    measure(parser.parse_args())
