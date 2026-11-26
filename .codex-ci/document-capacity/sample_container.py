"""所有する隔離 Linux container の cgroup と文書領域だけを集計する。"""

import argparse
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time

WORKSPACE = '/run/translacat-document-translation/work'
EVENTS = ('high', 'max', 'oom', 'oom_kill')
MEMORY = ('anon', 'file', 'shmem')


def inspect(identifier, owner):
    """固定 label と専用 internal network を確認し、外部経路を採用しない。"""
    process = subprocess.run(['docker', 'inspect', identifier], capture_output=True, timeout=10)
    if process.returncode:
        raise RuntimeError('OWNED_CONTAINER_UNAVAILABLE')
    value = json.loads(process.stdout)[0]
    if (value['Id'] != identifier
            or value['Config']['Labels'].get('translacat.document.capacity.owner') != owner):
        raise RuntimeError('CONTAINER_SCOPE_CHANGED')
    mode = value['HostConfig']['NetworkMode']
    if mode != 'none':
        networks = list(value['NetworkSettings']['Networks'].values())
        if len(networks) != 1 or networks[0]['NetworkID'] != mode:
            raise RuntimeError('ISOLATED_NETWORK_REQUIRED')
        network = json.loads(subprocess.check_output(['docker', 'network', 'inspect', mode], timeout=10))[0]
        if (network['Id'] != mode or not network['Internal']
                or network['Labels'].get('translacat.document.capacity.owner') != owner):
            raise RuntimeError('ISOLATED_NETWORK_REQUIRED')
    return value


def pairs(path, names):
    """既知のカウンタ名の数値だけを読む。"""
    result = {}
    for line in path.read_text().splitlines():
        key, raw = line.split()
        if key in names:
            result[key] = int(raw)
    if set(result) != set(names):
        raise RuntimeError('COUNTERS_UNAVAILABLE')
    return result


def group_for(pid, *, allow_root=False):
    """host PID の cgroup v2 を host の既知の mount 下に限定する。"""
    rows = Path(f'/proc/{pid}/cgroup').read_text().splitlines()
    paths = [row[3:] for row in rows if row.startswith('0::')]
    if len(paths) != 1 or '..' in Path(paths[0]).parts:
        raise RuntimeError('CGROUP_V2_REQUIRED')
    root = Path('/sys/fs/cgroup').resolve()
    group = (root / paths[0].lstrip('/')).resolve(strict=True)
    if not group.is_relative_to(root) or (group == root and not allow_root):
        raise RuntimeError('CGROUP_SCOPE_INVALID')
    return group


def require_outside(pid, target):
    """観測 PID が対象 cgroup とその子にいないことを確認する。"""
    if group_for(pid, allow_root=True).is_relative_to(target):
        raise RuntimeError('OBSERVER_INSIDE_BE_CGROUP')


def product_process(pid, group):
    """元コンテナ PID だけが cgroup を使うことを、観測ごとに確認する。"""
    if group.joinpath('cgroup.procs').read_text().split() != [str(pid)]:
        raise RuntimeError('UNEXPECTED_BE_CGROUP_PROCESS')
    lines = Path(f'/proc/{pid}/status').read_text().splitlines()
    values = {key: value.strip() for key, value in (line.split(':', 1) for line in lines)}
    if values.get('Name') != 'java' or group_for(pid) != group:
        raise RuntimeError('PRODUCT_JAVA_IDENTITY_CHANGED')
    return values


def mount_for(pid):
    """実 mountinfo の型・noswap・上限と、0700 領域の所有者を確認する。"""
    matches = []
    for line in Path(f'/proc/{pid}/mountinfo').read_text().splitlines():
        left, right = line.split(' - ', 1)
        fields, details = left.split(), right.split()
        point = re.sub(r'\\([0-7]{3})', lambda m: chr(int(m[1], 8)), fields[4])
        if WORKSPACE == point or WORKSPACE.startswith(point.rstrip('/') + '/'):
            matches.append((len(point), point, fields[5], details))
    if not matches:
        raise RuntimeError('WORKSPACE_MOUNT_UNAVAILABLE')
    _, point, options, details = max(matches)
    flags = set(options.split(',')) | set(details[2].split(','))
    if details[0] != 'tmpfs' or 'noswap' not in flags:
        raise RuntimeError('NOSWAP_TMPFS_REQUIRED')
    path = Path(f'/proc/{pid}/root' + WORKSPACE)
    metadata = path.stat()
    if not stat.S_ISDIR(metadata.st_mode) or stat.S_IMODE(metadata.st_mode) != 0o700:
        raise RuntimeError('PRIVATE_WORKSPACE_REQUIRED')
    volume = os.statvfs(path)
    if volume.f_frsize * volume.f_blocks != 33554432:
        raise RuntimeError('TMPFS_SIZE_CHANGED')
    return path, {'filesystem': 'tmpfs', 'mountPoint': point, 'noswap': True,
                  'mode': '0700', 'uid': metadata.st_uid, 'gid': metadata.st_gid,
                  'sizeBytes': volume.f_frsize * volume.f_blocks}


def footprint(path):
    """本文・名前を記録せず、有限個の文書ファイルの長さだけを集計する。"""
    pending, files, directories, total = [path], 0, 0, 0
    while pending:
        current = pending.pop()
        try:
            with os.scandir(current) as entries:
                for entry in entries:
                    try:
                        if entry.is_symlink():
                            raise RuntimeError('WORKSPACE_LINK_FORBIDDEN')
                        if entry.is_dir(follow_symlinks=False):
                            directories += 1
                            pending.append(Path(entry.path))
                        elif entry.is_file(follow_symlinks=False):
                            files += 1
                            total += entry.stat(follow_symlinks=False).st_size
                        if files + directories > 4096:
                            raise RuntimeError('WORKSPACE_ENTRY_BOUND')
                    except FileNotFoundError:
                        continue
        except FileNotFoundError:
            continue
    volume = os.statvfs(path)
    return {'fileBytes': total, 'files': files, 'directories': directories,
            'tmpfsUsedBytes': (volume.f_blocks - volume.f_bfree) * volume.f_frsize,
            'tmpfsAvailableBytes': volume.f_bavail * volume.f_frsize,
            'tmpfsUsedInodes': volume.f_files - volume.f_ffree}


def java_status(pid, group):
    """単一の製品 Java PID から resident の内訳だけを採る。"""
    names = ('VmRSS', 'VmHWM', 'VmSwap', 'RssAnon', 'RssFile', 'RssShmem')
    status = product_process(pid, group)
    return {key: int(status[key].split()[0]) * 1024 for key in names} | {'javaProcesses': 1}


def main():
    """最大 10 分だけ標本を採り、集計を保存する。container の変更や削除は行わない。"""
    parser = argparse.ArgumentParser()
    parser.add_argument('--container-id', required=True)
    parser.add_argument('--owner', required=True)
    parser.add_argument('--product-sha', required=True)
    parser.add_argument('--completed-file', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if (sys.platform != 'linux' or not re.fullmatch('[a-f0-9]{64}', args.container_id)
            or not re.fullmatch('[0-9]+-[0-9]+', args.owner)
            or not re.fullmatch('[a-f0-9]{40}', args.product_sha)):
        raise RuntimeError('LINUX_CI_SCOPE_REQUIRED')
    output = args.output.resolve()
    evidence = Path('evidence').resolve()
    completed = args.completed_file.resolve()
    ready = evidence / 'sampler-ready.json'
    if (not output.is_relative_to(evidence) or output.exists()
            or not completed.is_relative_to(evidence) or completed.exists()
            or ready.exists()):
        raise RuntimeError('OWNED_NEW_OUTPUT_REQUIRED')
    report = {'passed': False, 'owner': args.owner, 'containerId': args.container_id,
              'productSha': args.product_sha, 'scope': 'SAMPLING_ONLY_NOT_FUNCTIONAL_ACCEPTANCE',
              'samples': 0, 'intervalMs': 100, 'runtimeChanged': False,
              'observedPeakOnlyNotProductionProof': True, 'peaks': {}, 'minimums': {}}
    try:
        value = inspect(args.container_id, args.owner)
        if not value['State']['Running']:
            raise RuntimeError('CONTAINER_NOT_RUNNING')
        pid = value['State']['Pid']
        group = group_for(pid)
        require_outside(os.getpid(), group)
        report['observerOutsideBeCgroup'] = True
        report['bePid'] = pid
        workspace, mount = mount_for(pid)
        report['mount'] = mount
        report['memoryMax'] = group.joinpath('memory.max').read_text().strip()
        report['swapMax'] = group.joinpath('memory.swap.max').read_text().strip()
        if report['memoryMax'] != '536870912' or report['swapMax'] != '0':
            raise RuntimeError('BOUNDED_MEMORY_AND_ZERO_SWAP_REQUIRED')
        initial = pairs(group / 'memory.events', EVENTS)
        final = initial
        started = time.monotonic()
        maximum_swap = 0
        # jstat と最初の POST より先に、ホストから一つ目の標本を保存する。
        while time.monotonic() - started < 600:
            if not Path(f'/proc/{pid}/cgroup').is_file() or group_for(pid) != group:
                raise RuntimeError('CONTAINER_EXITED_BEFORE_FINAL_OBSERVATION')
            sample = {
                'memoryCurrent': int(group.joinpath('memory.current').read_text()),
                'memoryPeak': int(group.joinpath('memory.peak').read_text()),
                'swapCurrent': int(group.joinpath('memory.swap.current').read_text()),
                **pairs(group / 'memory.stat', MEMORY), **java_status(pid, group), **footprint(workspace),
            }
            maximum_swap = max(maximum_swap, sample['swapCurrent'])
            final = pairs(group / 'memory.events', EVENTS)
            for key, number in sample.items():
                report['peaks'][key] = max(report['peaks'].get(key, number), number)
                report['minimums'][key] = min(report['minimums'].get(key, number), number)
            report['samples'] += 1
            report['eventDeltas'] = {key: final[key] - initial[key] for key in EVENTS}
            if report['samples'] == 1:
                report['beforeJstatAndPost'] = sample
                temporary = ready.with_suffix('.tmp')
                temporary.write_text(json.dumps({'owner': args.owner, 'productSha': args.product_sha,
                                                 'containerId': args.container_id, 'bePid': pid,
                                                 'samplingReady': True, 'baseline': sample}))
                temporary.replace(ready)
            if completed.is_file():
                if completed.stat().st_size > 8192:
                    raise RuntimeError('COMPLETION_INPUT_BOUND')
                completion = json.loads(completed.read_text())
                if (set(completion) != {'owner', 'productSha', 'exitCode'}
                        or completion['owner'] != args.owner
                        or completion['productSha'] != args.product_sha
                        or type(completion['exitCode']) is not int):
                    raise RuntimeError('COMPLETION_IDENTITY_CHANGED')
                report['harnessExitCode'] = completion['exitCode']
                # 終了 marker の後も cgroup を生存させ、最後のイベントを必ず読む。
                final = pairs(group / 'memory.events', EVENTS)
                maximum_swap = max(maximum_swap, int(group.joinpath('memory.swap.current').read_text()))
                report['peaks']['memoryPeak'] = max(
                    report['peaks']['memoryPeak'], int(group.joinpath('memory.peak').read_text()))
                last = inspect(args.container_id, args.owner)
                if not last['State']['Running'] or last['State']['Pid'] != pid:
                    raise RuntimeError('CONTAINER_EXITED_BEFORE_FINAL_OBSERVATION')
                report['containerOomKilled'] = last['State']['OOMKilled']
                report['finalCgroupCountersAvailable'] = True
                break
            time.sleep(0.1)
        else:
            raise RuntimeError('SAMPLING_DEADLINE')
        report['eventDeltas'] = {key: final[key] - initial[key] for key in EVENTS}
        report['elapsedMs'] = round((time.monotonic() - started) * 1000)
        report['passed'] = (report['samples'] > 0 and maximum_swap == 0
                            and report.get('harnessExitCode') == 0
                            and not report.get('containerOomKilled')
                            and all(report['eventDeltas'][key] == 0 for key in ('oom', 'oom_kill')))
    except Exception as failure:
        # cgroup が消えた場合は最後に成功した標本と区別して保持する。
        report.setdefault('finalCgroupCountersAvailable', False)
        report['errorType'] = type(failure).__name__
        report['fixedFailure'] = (str(failure) if re.fullmatch('[A-Z_]+', str(failure))
                                  else 'SAMPLING_UNAVAILABLE')
    output.write_text(json.dumps(report, indent=2))
    print(json.dumps({'passed': report['passed'], 'samples': report['samples'],
                      'fixedFailure': report.get('fixedFailure')}))
    if not report['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
