"""実製品 bootJar を隔離 MySQL/合成モデルに接続し、HTTP と cgroup を同時に検査する。"""

import argparse
from datetime import datetime, timezone
from hashlib import file_digest
import json
import os
import platform
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import threading
import time
import uuid

import mac_runtime as previous
from linux_stack import LinuxStack
from linux_document_capacity_http import run_capacity_http
from sample_container import group_for, inspect, product_process, require_outside

ROOT = Path(__file__).resolve().parent
HISTORICAL_JAR_SHA256 = '12b089388fb6af874a1dfb4183faf9a545585b2584e0ad70de7758b653858308'


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


def perfdata_uri(pid, group):
    """元の Java PID が公開した固定 tmpfs カウンタだけを file: で読む。"""
    status = product_process(pid, group)
    if status['NSpid'].split()[-1] != '1' or status['Uid'].split()[0] != '0':
        raise RuntimeError('EXPECTED_BE_JAVA_PID_ONE')
    path = Path(f'/proc/{pid}/root/tmp/hsperfdata_root/1')
    if path.parent.is_symlink():
        raise RuntimeError('BE_PERFDATA_SCOPE')
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid != 0 or not 0 < info.st_size <= 1048576:
        raise RuntimeError('BE_PERFDATA_SCOPE')
    return path.as_uri()


def diagnosis(report):
    """機能・観測・コンテナ OOM の帰結を固定コードにする。"""
    if report['passed']:
        return 'ISOLATED_512MIB_PASS'
    if report.get('backendExitState', {}).get('oomKilled') is True:
        return 'BE_CONTAINER_OOM'
    sampler_failure = report.get('sampling', {}).get('fixedFailure')
    telemetry_failures = {'HOST_HEAP_TELEMETRY_UNAVAILABLE', 'CAPACITY_SAMPLER_READY_TIMEOUT',
                          'CAPACITY_SAMPLER_EXITED_EARLY', 'OBSERVER_INSIDE_BE_CGROUP',
                          'UNEXPECTED_BE_CGROUP_PROCESS', 'BE_PERFDATA_SCOPE'}
    if report.get('fixedFailure') in telemetry_failures or sampler_failure in telemetry_failures:
        return 'HOST_TELEMETRY_INCOMPLETE'
    if report.get('harnessExitCode') != 0:
        return 'FUNCTIONAL_OR_SETUP_FAILURE'
    return 'ISOLATED_EVIDENCE_INCOMPLETE'


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
    jar_sha = digest(jar)
    if jar.is_symlink() or not re.fullmatch('[a-f0-9]{64}', args.jar_sha256) or jar_sha != args.jar_sha256:
        raise RuntimeError('BOOT_JAR_SHA_MISMATCH')
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
    heap = {'samples': 0, 'sampledPeakHeapUsedBytes': None,
            'jstatOverheadIncludedInCgroup': False, 'jstatOutsideBeCgroup': False}
    first_heap = threading.Event()
    be_group = None
    started = time.monotonic()
    report = {'atUtc': datetime.now(timezone.utc).isoformat(), 'phase': args.phase,
              'productSha': args.product_sha, 'jarSha256': jar_sha, 'owner': owner,
              'historicalJarSha256': HISTORICAL_JAR_SHA256,
              'historicalJarByteMatch': jar_sha == HISTORICAL_JAR_SHA256,
              'comparison': ('HISTORICAL_JAR_BYTE_EXACT' if jar_sha == HISTORICAL_JAR_SHA256
                             else 'SAME_SOURCE_REBUILT_JAR'),
              'javaXmxMiB': 256, 'jstatMode': 'HOST_FILE_PERFDATA',
              'memoryLimitMiB': args.memory_mib, 'passed': False, 'operatingRequests': 0,
              'paidProviderCalls': 0, 'productionCapacityProven': False, 'checkpoints': [],
              'kernelRelease': platform.release(), 'cgroupVersion': 2,
              'dockerServerVersion': subprocess.check_output(
                  ['docker', 'version', '--format', '{{.Server.Version}}'], timeout=10).decode().strip()}
    completed = evidence / 'completed.json'

    def checkpoint(label):
        """実際に到達した段階とその時の kernel カウンタだけを記録する。"""
        mark = {'phase': label, 'elapsedMs': round((time.monotonic() - started) * 1000)}
        if be_group is not None:
            try:
                mark.update({field: int(be_group.joinpath(name).read_text())
                             for field, name in (('memoryCurrentBytes', 'memory.current'),
                                                 ('memoryPeakBytes', 'memory.peak'),
                                                 ('swapCurrentBytes', 'memory.swap.current'))})
            except FileNotFoundError:
                mark['cgroupSnapshotAvailable'] = False
        report['checkpoints'].append(mark)

    try:
        stack.start()
        stack.start_mysql(source)
        stack.start_provider(ROOT / 'fixture_provider.py')
        stack.prepare_be(source, jar)
        if digest(jar) != jar_sha:
            raise RuntimeError('BOOT_JAR_SHA_MISMATCH')
        stack.start_be()
        report['containerId'] = stack.containers['be']
        backend = inspect(report['containerId'], owner)
        if (not backend['State']['Running'] or backend['Config']['Cmd'].count('-Xmx256m') != 1):
            raise RuntimeError('EXACT_BE_RUNTIME_REQUIRED')
        be_pid = backend['State']['Pid']
        be_group = group_for(be_pid)
        require_outside(os.getpid(), be_group)
        report['harnessOutsideBeCgroup'] = True
        checkpoint('be_started')
        stack.start_host_relay()
        stack.wait_be_ready()
        checkpoint('be_health_ready')
        _login, token = stack.seed_account(Path(shutil.which('java')), source, jar, 'Owner')
        _other, other = stack.seed_account(Path(shutil.which('java')), source, jar, 'Other')
        checkpoint('synthetic_logins_complete')
        sampler = subprocess.Popen([
            sys.executable, str(ROOT / 'sample_container.py'), '--container-id', stack.containers['be'],
            '--owner', owner, '--product-sha', args.product_sha,
            '--completed-file', str(completed), '--output', str(evidence / 'sampling.json')],
            cwd=phase, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        ready = evidence / 'sampler-ready.json'
        wait_file(ready, sampler, 20)
        marker = json.loads(ready.read_text())
        if (marker['owner'] != owner or marker['productSha'] != args.product_sha
                or marker['containerId'] != report['containerId'] or marker['bePid'] != be_pid
                or marker['samplingReady'] is not True):
            raise RuntimeError('CAPACITY_SAMPLER_IDENTITY')
        require_outside(sampler.pid, be_group)
        checkpoint('baseline_before_jstat')

        # BE を exec せず、host JVM の jstat が専用 /tmp の読み取り専用 PerfData を参照する。
        executable = Path(os.environ['JAVA_HOME']) / 'bin/jstat'
        try:
            jstat = subprocess.Popen([str(executable), '-gc', perfdata_uri(be_pid, be_group), '100', '6000'],
                                     stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
            require_outside(jstat.pid, be_group)
        except OSError as unavailable:
            raise RuntimeError('HOST_HEAP_TELEMETRY_UNAVAILABLE') from unavailable
        heap['jstatOutsideBeCgroup'] = True

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
                    peak = heap['sampledPeakHeapUsedBytes']
                    heap['samples'] += 1
                    heap['sampledPeakHeapUsedBytes'] = used if peak is None else max(peak, used)
                    first_heap.set()

        reader = threading.Thread(target=read_heap, daemon=True)
        reader.start()
        if not first_heap.wait(8):
            raise RuntimeError('HOST_HEAP_TELEMETRY_UNAVAILABLE')
        checkpoint('host_observers_ready')

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
        checkpoint('before_first_http_case')

        report['http'] = run_capacity_http(stack, port=previous.BE_PORT, input_dir=ROOT / 'fixtures',
                                           owner=token, other=other, checkpoint=checkpoint, isolated_fixture=True)
        report['harnessExitCode'] = 0
        heap['jstatAliveAtHttpCompletion'] = jstat.poll() is None
        if not heap['jstatAliveAtHttpCompletion']:
            raise RuntimeError('HOST_HEAP_TELEMETRY_UNAVAILABLE')
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
        if jstat is not None and jstat.poll() is None:
            jstat.terminate()
            try:
                jstat.wait(timeout=5)
            except subprocess.TimeoutExpired:
                jstat.kill()
                jstat.wait(timeout=5)
        if reader:
            reader.join(timeout=5)
        heap['jstatStopped'] = jstat is None or jstat.poll() is not None
        report['heap'] = heap
        report['cleanup'] = stack.close()
        sampling = report.get('sampling', {})
        report['passed'] = (report.get('harnessExitCode') == 0 and sampling.get('passed') is True
                             and sampling.get('observerOutsideBeCgroup') is True
                             and sampling.get('finalCgroupCountersAvailable') is True
                             and heap['jstatOutsideBeCgroup'] and heap['jstatStopped'] and heap['samples'] > 0
                             and heap.get('jstatAliveAtHttpCompletion') is True
                             and db_states['samples'] > 0 and db_states['errors'] == 0
                             and db_states['maxActiveOrReady'] <= 1
                             and report['cleanup']['owned_resources_removed']
                             and report['cleanup']['owned_volatile_mounts_removed'])
        report['migrationNames'] = selected
        report['images'] = images
        (evidence / 'capacity-result.json').write_text(json.dumps(report, indent=2))
        # 合成キーと設定も自分の phase 下の領域から回収する。
        if not work.resolve().is_relative_to(phase) or work.is_symlink():
            raise RuntimeError('OWNED_WORK_PATH_CHANGED')
        if report['cleanup']['owned_resources_removed']:
            shutil.rmtree(work)
        report['ownedWorkRemoved'] = not work.exists()
        report['passed'] = report['passed'] and report['ownedWorkRemoved']
        report['diagnosis'] = diagnosis(report)
        (evidence / 'capacity-result.json').write_text(json.dumps(report, indent=2))
    print(json.dumps({'phase': args.phase, 'passed': report['passed'],
                      'fixedFailure': report.get('fixedFailure'), 'cleanup': report['cleanup']}))
    if not report['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--product-dir', type=Path, required=True)
    parser.add_argument('--product-sha', required=True)
    parser.add_argument('--jar-sha256', required=True)
    parser.add_argument('--memory-mib', type=int, choices=(512,), required=True)
    parser.add_argument('--phase', choices=('generous', 'bounded'), required=True)
    measure(parser.parse_args())
