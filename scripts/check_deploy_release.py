"""Failure-injection tests; every Docker/Git/HTTP/lock operation is fake.
Run: python scripts/check_deploy_release.py
Only synthetic credentials/dictionary fixtures are used; no production env is read.
"""
from __future__ import annotations

import base64
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "deploy-release.sh"
VALIDATOR = ROOT / "scripts" / "validate-release-env.py"
NAME = re.search(r'^CONTAINER="([^"]+)"', SCRIPT.read_text(encoding="utf-8"), re.MULTILINE).group(1)
SHA = "1" * 40
FAKE = r'''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
name = Path(sys.argv[0]).name
args = sys.argv[1:]
state_path = Path(os.environ["PROBE_STATE"])
state = json.loads(state_path.read_text())
fault = os.environ["PROBE_FAULT"]
container = os.environ["PROBE_CONTAINER"]
sha = os.environ["PROBE_SHA"]
with open(os.environ["PROBE_CALLS"], "a") as out:
    out.write(json.dumps([name, *args]) + "\n")

def end(code=0, output=""):
    state_path.write_text(json.dumps(state))
    if output: print(output)
    sys.exit(code)
if name == "git":
    if args[0] == "diff": end(1 if fault == "dirty" else 0)
    if "--error-unmatch" in args: end(0 if fault == "tracked_env" else 1)
    if "--others" in args:
        if fault == "worktree_list": end(15)
        paths = ["unexpected.txt"] if fault == "untracked" else [".env", "sudachi/system_full.dic"]
        if fault == "dictionary": paths = [".env"]
        sys.stdout.write("\0".join(paths) + "\0")
        end()
    end(0, "0" * 40 if fault == "checkout" else sha)
if name == "sleep": end()
if name == "flock": end(1 if fault == "lock" else 0)
if name == "curl":
    new = state.get(container, {}).get("sha") == sha
    failed = fault in ("health", "rollback_start", "rollback_rename", "rollback_remove", "rollback_health")
    code = "503" if (new and failed) or fault == "rollback_health" else "200"
    body = {"resultCode": 200, "body": "WRONG" if new and fault == "health_body" else "OK"}
    Path(args[args.index("--output") + 1]).write_text(json.dumps(body))
    end(28 if new and fault == "health_timeout" else 0, code)
if args[:2] == ["network", "inspect"]: end(1)
if args[:2] == ["network", "create"]: end(1 if fault == "network" else 0, "fake-network")
if args[:2] == ["volume", "inspect"]:
    if fault == "volume_foreign": end(0, "unowned" if "--format" in args else "existing-volume")
    end(1)
if args[:2] == ["volume", "create"]: end(17 if fault == "volume" else 0, "fake-owned-volume")
if args[0] == "build": end(124 if fault == "build_timeout" else 7 if fault == "build" else 0)
if args[:2] == ["image", "inspect"]: end(0, "0" * 40 if fault == "image_revision" else sha)
if args[:2] == ["container", "ls"]:
    end(1 if fault == "inspect_unavailable" else 0, "old-id" if container in state else "")
if args[0] == "inspect":
    item = state.get(args[-1])
    if item is None: end(1)
    fmt = args[2]
    if ".Id" in fmt: end(0, item["id"])
    if "Running" in fmt: end(0, str(item["running"]).lower())
    end(0, "0" * 40 if fault == "revision" else item["sha"])
if args[0] == "stop":
    state[args[-1]]["running"] = False
    end(9 if fault == "stop" else 0)
if args[0] == "rename":
    old, new = args[1:]
    if fault == "rename" and old == container: end(10)
    if fault == "rollback_rename" and old != container: end(10)
    if old not in state or new in state: end(1)
    state[new] = state.pop(old)
    end()
if args[0] == "run":
    if "--rm" in args: end(14 if fault == "image_runtime" else 0)
    if fault == "run": end(11)
    target = args[args.index("--name") + 1]
    state[target] = {"id": "new-id", "running": True, "sha": sha, "env": "new-env", "mount": "new-mount"}
    end(0, "new-container-id")
if args[0] == "start":
    if fault == "rollback_start": end(12)
    state[args[-1]]["running"] = True
    end()
if args[0] == "rm":
    if fault == "rollback_remove": end(13)
    state.pop(args[-1], None)
    end()
end(99, "unsupported probe call: " + repr(args))
'''


def bash_executable() -> str:
    """Use the existing Git Bash on Windows, without installing a runtime."""
    if os.name == "nt":
        for candidate in (os.environ.get("GIT_BASH"), r"C:\Program Files\Git\bin\bash.exe", r"C:\Program Files\Git\usr\bin\bash.exe"):
            if candidate and Path(candidate).is_file():
                return candidate
    return shutil.which("bash") or sys.exit("Bash is required; no live deployment is performed.")


def path_for_bash(bash: str, value: Path | str) -> str:
    if os.name != "nt":
        return str(value)
    return subprocess.run([bash, "-lc", 'cygpath -u "$1"', "bash", str(value)],
                          capture_output=True, text=True, check=True).stdout.strip()


def synthetic_environment() -> bytes:
    spec = importlib.util.spec_from_file_location("release_env", VALIDATOR)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    values = dict.fromkeys(module.REQUIRED, "test-only-value") | module.EXPECTED
    values["NOVEL_AI_SERVER_API_KEY"] = "novel-only-synthetic-authentication-key"
    for key in ("JWT_SECRET_KEY", "LL_INTERNAL_JWT_SECRET_BASE64", "CHAT_GATEWAY_SECRET_BASE64", "CHAT_CORE_IDENTITY_SECRET_BASE64"):
        values[key] = base64.b64encode((key + "x" * 64).encode()[:32]).decode()
    values["DB_URL"] = "jdbc:log4jdbc:mysql://synthetic.tidbcloud.com:4000/translacat"
    for key in ("GOOGLE_PROXY_URL", "CLOUDFLARE_R2_ENDPOINT", "CLOUDFLARE_R2_PUBLIC_BASE_URL"):
        values[key] = "https://synthetic.invalid"
    raw = "".join(f"{key}={value}\n" for key, value in values.items()).encode()
    assert not module.validate(raw)

    # 검증: Docker가 문자 그대로 소비할 특수문자와 잘못된 운영 입력을 구분한다.
    assert not module.validate(raw + b"EXTRA_SECRET=a=$'b\"+/\\\n")
    for invalid in (b"\xef\xbb\xbf" + raw, raw.replace(b"\n", b"\r\n"), raw + b"DB_URL=duplicate\n",
                    raw.replace(b"AI_SERVER_API_KEY=test-only-value", b"AI_SERVER_API_KEY="),
                    raw.replace(b"https://161.33.34.50:8443", b"http://localhost:8000"),
                    raw.replace(b"DB_PASSWORD=test-only-value", b"DB_PASSWORD=CHANGE_ME"),
                    raw.replace(b"JWT_SECRET_KEY=", b"JWT_SECRET_KEY=!")):
        assert module.validate(invalid), "invalid environment was accepted"
    # 검증: 가용성 토글 재도입과 소설 인증의 공유키 fallback을 거부한다.
    for key in module.RETIRED_AVAILABILITY_KEYS:
        assert module.validate(raw + f"{key}=false\n".encode())
    assert module.validate(raw.replace(values["NOVEL_AI_SERVER_API_KEY"].encode(), b"short"))
    assert module.validate(raw.replace(b"AI_SERVER_API_KEY=test-only-value", b"AI_SERVER_API_KEY=" + values["NOVEL_AI_SERVER_API_KEY"].encode()))
    print("PASS environment validator: valid/special characters + 17 invalid fixtures")
    return raw


def main() -> None:
    bash = bash_executable()
    script_for_bash = path_for_bash(bash, SCRIPT)
    python_for_bash = path_for_bash(bash, sys.executable)
    raw = synthetic_environment()
    results = []
    faults = ("checkout", "lock", "dirty", "tracked_env", "untracked", "worktree_list", "env_directory", "environment", "dictionary", "network", "volume", "volume_foreign", "build", "build_timeout",
              "image_revision", "image_runtime", "inspect_unavailable", "stop", "rename", "run", "revision", "health",
              "health_body", "health_timeout", "success", "rollback_start", "rollback_rename", "rollback_remove", "rollback_health")
    cases = [(name, True, True) for name in faults]
    cases += [("health", True, False), ("success", False, False), ("health", False, False)]
    for fault, exists, running in cases:
        # 준비: 격리된 fixture만 만들고 모든 외부 명령을 명시적인 fake로 교체한다.
        with tempfile.TemporaryDirectory(prefix="translacat-release-probe-") as directory:
            root = Path(directory)
            bins = root / "bin"
            bins.mkdir()
            (root / "scripts").mkdir()
            shutil.copyfile(VALIDATOR, root / "scripts" / VALIDATOR.name)
            (root / "sudachi").mkdir()
            if fault == "env_directory":
                (root / ".env").mkdir()
            else:
                (root / ".env").write_text("EXISTING=preserve-exactly\n")
            if fault != "dictionary":
                (root / "sudachi" / "system_full.dic").write_bytes(b"FAKE-FOR-COMMAND-STUB-ONLY" * 50000)
            original = {NAME: {"id": "old-id", "running": running, "sha": "old-sha", "env": "old-env", "mount": "old-mount"}} if exists else {}
            (root / "state.json").write_text(json.dumps(original))
            (root / "calls.jsonl").write_text("")
            for command in ("git", "docker", "curl", "sleep", "flock"):
                executable = bins / command
                executable.write_text(FAKE.replace("#!/usr/bin/env python3", "#!" + python_for_bash + " -S"), newline="\n")
                executable.chmod(0o755)
            wrappers = {
                "timeout": '#!/usr/bin/env bash\nshift; shift; exec "$@"\n',
                "python3": '#!/usr/bin/env bash\nexec ' + "'" + python_for_bash.replace("'", "'\\''") + "'" + ' -S "$@"\n',
            }
            for command, content in wrappers.items():
                (bins / command).write_text(content, newline="\n")
                (bins / command).chmod(0o755)
            environment = os.environ | {
                "PROBE_STATE": str(root / "state.json"), "PROBE_CALLS": str(root / "calls.jsonl"),
                "PROBE_FAULT": fault, "PROBE_CONTAINER": NAME, "PROBE_SHA": SHA,
                "DEPLOY_ENV_B64": "%%%bad-base64" if fault == "environment" else base64.b64encode(raw).decode(),
                "DEPLOY_HEALTH_ATTEMPTS": "2", "DEPLOY_HEALTH_DELAY_SECONDS": "0", "DEPLOY_ROLLBACK_ATTEMPTS": "2",
                "PROBE_HOME": path_for_bash(bash, root),
            }
            environment.pop("DEPLOY_LOCK_FD", None)

            # 실행: fake PATH를 login shell 초기화 이후 설정하며 timeout으로 테스트 자체도 제한한다.
            process = subprocess.run([
                bash, "-lc", 'export PATH="$1:$PATH" HOME="$PROBE_HOME" TMPDIR="$PROBE_HOME"; exec bash "$2" "$3"',
                "bash", path_for_bash(bash, bins), script_for_bash, SHA,
            ], cwd=root, env=environment, capture_output=True, text=True, timeout=30)
            after = json.loads((root / "state.json").read_text())
            calls = [json.loads(line) for line in (root / "calls.jsonl").read_text().splitlines()]

            # 검증: 성공 여부뿐 아니라 원래 container ID/env/mount 보존과 비밀 파일 cleanup을 확인한다.
            assert not list(root.glob("translacat-be-env.*")), (fault, "temporary environment leaked")
            assert not list(root.glob("translacat-be-health.*")), (fault, "temporary health file leaked")
            assert not list(root.glob("translacat-be-worktree.*")), (fault, "temporary worktree file leaked")
            if fault != "env_directory":
                assert (root / ".env").read_text() == "EXISTING=preserve-exactly\n", "existing env was modified"
            if fault == "success":
                assert process.returncode == 0, process.stderr
                assert after[NAME]["sha"] == SHA and after[NAME]["running"]
                create_volume = next(i for i, call in enumerate(calls) if call[:3] == ["docker", "volume", "create"])
                replacement = next(i for i, call in enumerate(calls) if call[:2] == ["docker", "run"] and "--name" in call)
                assert create_volume < replacement
                mounts = [value for value in calls[replacement] if "novel-audio-spool" in value]
                # Git Bash는 Windows fake 실행 전에 Linux 목적지에 Git 설치 경로를 덧붙일 수 있다.
                assert any(value.startswith("type=volume,src=translacat-be-novel-audio-spool,dst=")
                           and value.endswith("/app/data/novel-audio-spool") for value in mounts), mounts
                if exists:
                    backups = [item for key, item in after.items() if key != NAME]
                    assert len(backups) == 1 and backups[0] == (original[NAME] | {"running": False})
            else:
                assert process.returncode != 0, (fault, "failure reported success")
                if fault.startswith("rollback_"):
                    assert process.returncode == 70 and "CRITICAL" in process.stderr, (fault, process.stderr)
                    preserved = [item for item in after.values() if item["id"] == "old-id"]
                    assert len(preserved) == 1 and preserved[0]["env"] == "old-env" and preserved[0]["mount"] == "old-mount"
                else:
                    assert after == original, (fault, original, after, process.stderr)
                if fault in ("checkout", "lock", "dirty", "tracked_env", "untracked", "worktree_list", "env_directory", "environment", "dictionary", "network", "volume", "volume_foreign", "build", "build_timeout", "image_revision", "image_runtime", "inspect_unavailable"):
                    assert not any(call[:2] == ["docker", "stop"] for call in calls), (fault, calls)
            results.append({"fault": fault, "previous_exists": exists, "previous_running": running,
                            "exit": process.returncode, "assertions": "PASS"})
            print(f"PASS {fault}: previous={exists}/{running}, exit={process.returncode}")
    print(f"{len(results)}/{len(results)} failure-injection scenarios passed. External operations were fake.")


if __name__ == "__main__":
    main()
