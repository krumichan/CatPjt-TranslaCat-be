"""隔離 Linux の実 BE HTTP・文書 DB・一時領域をまとめて確認する。"""

from __future__ import annotations

import contextlib
import hashlib
import http.client
import io
import json
import sys
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from zipfile import ZipFile

BASE = "/api/v1/document-translations"
LIMIT = 10 * 1024 * 1024
EXPECTED = {"normal.pptx", "expanded-under.docx", "expanded-over.docx", "transfer-near-cap.docx"}
MEDIA = {
    "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
}


# 期待した点以外の例外を吸収せず、固定の検収失敗コードで止める。
def require(condition, code):
    if not condition:
        raise RuntimeError(code)


# Linux ランナーが所有する loopback だけを使い、入力は固定 manifest で検証する。
def fixtures(directory):
    directory = Path(directory).resolve(strict=True)
    manifest = json.loads((directory / "input-manifest.json").read_bytes())
    require(manifest.get("synthetic") is True and set(manifest["files"]) == EXPECTED, "CAPACITY_INPUT_SCOPE")
    result = {}
    for name, item in manifest["files"].items():
        path = directory / name
        require(path.resolve(strict=True) == path and 0 < path.stat().st_size <= LIMIT, "CAPACITY_INPUT_PATH")
        data = path.read_bytes()
        require(hashlib.sha256(data).hexdigest() == item["sha256"]
                and len(data) == item["compressedBytes"], "CAPACITY_INPUT_SHA")
        result[name] = data
    return result


# HTTP と DB では新しい合成仕事の UUID だけを使う。
def identifier(value):
    canonical = str(uuid.UUID(value))
    require(canonical == value, "CAPACITY_UUID")
    return canonical


# 自家発行された試験 token を同一の loopback BE へだけ渡し、本文をログに残さない。
class CapacityHttp:
    def __init__(self, stack, port):
        require(sys.platform.startswith("linux") and type(port) is int and 1024 <= port <= 65535,
                "CAPACITY_LINUX_LOOPBACK")
        self.stack = stack
        self.port = port

    def call(self, token, method, route, body=None, extra=None):
        """小さな HTTP 証拠だけを読み、socket は常に閉じる。"""
        headers = {"Authorization": "Bearer " + token}
        with contextlib.closing(http.client.HTTPConnection("127.0.0.1", self.port, timeout=65)) as conn:
            conn.request(method, route, body=body, headers=headers | (extra or {}))
            with conn.getresponse() as response:
                payload = response.read(65537)
                require(len(payload) <= 65536, "CAPACITY_METADATA_HTTP_BOUND")
                return response.status, payload

    def sql(self, query):
        """既存 fixture adapter の繰り返し可能な読み取りだけを使う。"""
        return self.stack.sql(query).stdout.splitlines()

    def submit(self, token, name, raw):
        """ファイル種類・サイズ・専用 UUID を固定した実際の octet-stream 入力を行う。"""
        request_key = str(uuid.uuid4())
        extension = Path(name).suffix[1:]
        route = BASE + f"?format={extension}&sourceLanguage=ja&targetLanguage=en"
        status, raw_response = self.call(token, "POST", route, raw, {
            "Content-Type": "application/octet-stream", "Content-Length": str(len(raw)),
            "Idempotency-Key": request_key,
        })
        data = json.loads(raw_response)
        if status == 202:
            return status, request_key, identifier(data["jobId"])
        require(status == 503 and data.get("code") == "SERVICE_UNAVAILABLE", "CAPACITY_UNEXPECTED_SUBMIT_STATUS")
        return status, request_key, None

    def check_denied(self, request_key):
        """503 と同じ冪等キーの DB 仕事が存在しないことを確認する。"""
        request_key = identifier(request_key)
        require(self.sql("SELECT COUNT(*) FROM document_translation_job "
                         f"WHERE idempotency_key='{request_key}';") == [b"0"], "CAPACITY_DENIED_INSERTED")

    def wait_status(self, token, job, state, seconds):
        """終端まで有限に待ち、失敗済みの仕事を成功扱いしない。"""
        limit = time.monotonic() + seconds
        while time.monotonic() < limit:
            status, payload = self.call(token, "GET", BASE + "/" + job)
            data = json.loads(payload)
            require(status == 200 and data["jobId"] == job, "CAPACITY_STATUS_IDENTITY")
            if data["status"] == state:
                return data
            require(data["status"] not in {"FAILED", "EXPIRED", "CANCELLED"}, "CAPACITY_WRONG_TERMINAL")
            time.sleep(0.4)
        raise RuntimeError("CAPACITY_STATUS_TIMEOUT")

    def entries(self, job):
        """同じ job の所有された実 Linux ファイルだけを確認する。"""
        base = "job-" + job
        return {entry for entry in self.stack.workspace_entries()
                if entry == base or entry.startswith(base + "/")}

    def ready(self, token, job):
        """出力準備時の一つの結果と、元データの消滅を確認する。"""
        row = self.wait_status(token, job, "SUCCEEDED", 175)
        files = self.entries(job)
        result = "job-" + job + "/result"
        require(result in files and files <= {result, "job-" + job}, "CAPACITY_READY_FILES")
        require(type(row["outputSizeBytes"]) is int and 0 < row["outputSizeBytes"] <= LIMIT,
                "CAPACITY_READY_SIZE")
        require(row["downloadPath"] == BASE + "/" + job + "/download", "CAPACITY_DOWNLOAD_SCOPE")
        return row

    def cleared(self, job, seconds=12):
        """ローカルファイルと実 DB の本文参照が両方消えたときだけ完了とする。"""
        limit = time.monotonic() + seconds
        query = (
            "SELECT COUNT(*) FROM document_translation_job "
            f"WHERE id='{job}' AND input_key IS NULL AND output_key IS NULL AND output_sha256 IS NULL "
            "AND input_sha256=REPEAT('0',64);"
        )
        while time.monotonic() < limit:
            if not self.entries(job) and self.sql(query) == [b"1"]:
                return
            time.sleep(0.4)
        raise RuntimeError("CAPACITY_CLEANUP_TIMEOUT")

    def save(self, token, other, job, ready):
        """最初の GET ヘッダー時点のパス不在、ZIP 実体、二度目の拒否を確認する。"""
        path = ready["downloadPath"]
        require(self.call(other, "GET", path)[0] == 404, "CAPACITY_NON_OWNER_STATUS")
        with contextlib.closing(http.client.HTTPConnection("127.0.0.1", self.port, timeout=65)) as conn:
            conn.request("GET", path, headers={"Authorization": "Bearer " + token})
            with conn.getresponse() as response:
                require(response.status == 200 and response.getheader("Content-Type") == MEDIA[ready["format"]],
                        "CAPACITY_DOWNLOAD_HEADERS")
                require(not self.entries(job), "CAPACITY_WORKSPACE_AT_FIRST_HEADERS")
                body = response.read(LIMIT + 1)
        require(len(body) == ready["outputSizeBytes"], "CAPACITY_DOWNLOAD_BYTES")
        with ZipFile(io.BytesIO(body)) as output:
            require(output.testzip() is None, "CAPACITY_DOWNLOAD_ZIP")
        require(self.call(token, "GET", path)[0] == 410, "CAPACITY_DUPLICATE_GET")
        self.cleared(job)
        return {"resultBytes": len(body), "resultSha256": hashlib.sha256(body).hexdigest(),
                "workspaceAbsentAtFirstHeaders": True, "nextGet": 410}


# このエントリは既存 Linux fixture runner に組み込む。運用状態の DB を扱わない。
def run_capacity_http(stack, *, port, input_dir, owner, other, checkpoint, isolated_fixture=False):
    """Run with fresh synthetic accounts and the owning Linux CI stack; never persist tokens or documents."""
    require(isolated_fixture is True and owner and other and owner != other, "CAPACITY_ISOLATED_FIXTURE")
    api = CapacityHttp(stack, port)
    source = fixtures(input_dir)
    require(api.sql("SELECT COUNT(*) FROM document_translation_job;"
                    "SELECT COUNT(*) FROM document_translation_batch;"
                    "SELECT COUNT(*) FROM document_translation_object_write;") == [b"0", b"0", b"0"],
            "CAPACITY_FIXTURE_DB_NOT_EMPTY")
    require(not any(name.startswith("job-") for name in stack.workspace_entries()), "CAPACITY_FIXTURE_NOT_EMPTY")
    report = {}
    accepted = []

    # 正常ケースと展開境界は同じ HTTP・DB・物理削除契約を使用する。
    for label, name in (("normal", "normal.pptx"), ("expanded_under", "expanded-under.docx")):
        checkpoint(label + "_start")
        code, _, job = api.submit(owner, name, source[name])
        require(code == 202 and job is not None, "CAPACITY_EXPECTED_ACCEPT")
        accepted.append(job)
        report[label] = api.save(owner, other, job, api.ready(owner, job))
        checkpoint(label + "_end")
    checkpoint("expanded_over_start")
    code, _, job = api.submit(owner, "expanded-over.docx", source["expanded-over.docx"])
    require(code == 202 and job is not None, "CAPACITY_EXPECTED_VALIDATION_JOB")
    accepted.append(job)
    item = api.wait_status(owner, job, "FAILED", 175)
    require(item.get("errorCode") == "DOCUMENT_EXPANDED_SIZE_LIMIT" and not item.get("downloadPath"),
            "CAPACITY_EXPANDED_REJECTION")
    api.cleared(job)
    report["expanded_over"] = {"error": "DOCUMENT_EXPANDED_SIZE_LIMIT", "cleaned": True}
    checkpoint("expanded_over_end")

    # 大きい結果を未受領のまま保持し、別所有者の受信・予約枯渇・期限後の再入場を測る。
    checkpoint("unclaimed_and_full_transfer_start")
    name = "transfer-near-cap.docx"
    code, _, job = api.submit(owner, name, source[name])
    require(code == 202 and job is not None, "CAPACITY_EXPECTED_TRANSFER_ACCEPT")
    accepted.append(job)
    ready = api.ready(owner, job)
    began = time.monotonic()
    code, denied, _ = api.submit(other, name, source[name])
    require(code == 503 and api.entries(job), "CAPACITY_READY_RESERVATION")
    api.check_denied(denied)
    require({p.split("/", 1)[0] for p in stack.workspace_entries() if p.startswith("job-")} == {"job-" + job},
            "CAPACITY_DENIED_WORKSPACE_EXISTS")
    checkpoint("unclaimed_denied_503")
    api.cleared(job, 135)
    expired = time.monotonic() - began
    require(118 <= expired <= 135 and api.call(owner, "GET", ready["downloadPath"])[0] == 410,
            "CAPACITY_READY_EXPIRY")
    code, _, next_job = api.submit(other, name, source[name])
    require(code == 202 and next_job is not None, "CAPACITY_RELEASE_NOT_REUSABLE")
    accepted.append(next_job)
    report["unclaimed"] = {"saturatedStatus": 503, "expiredSeconds": round(expired, 3),
                           "reclaimedAndReused": True, "newResult": api.save(other, owner, next_job, api.ready(other, next_job))}
    checkpoint("unclaimed_and_full_transfer_end")

    # 転送を同時に開始し、中途失敗と予約拒否を混同しないよう両方の応答と DB を照合する。
    checkpoint("concurrent_start")
    barrier = threading.Barrier(2)
    def simultaneous(token):
        barrier.wait(timeout=5)
        return token, api.submit(token, "expanded-under.docx", source["expanded-under.docx"])
    with ThreadPoolExecutor(max_workers=2) as workers:
        futures = [workers.submit(simultaneous, token) for token in (owner, other)]
        responses = [future.result(timeout=75) for future in futures]
    require(sorted(row[1][0] for row in responses) == [202, 503], "CAPACITY_CONCURRENT_STATUSES")
    for token, (code, key, job) in responses:
        if code == 503:
            api.check_denied(key)
        else:
            accepted.append(job)
            report["concurrent"] = api.save(token, other if token == owner else owner, job, api.ready(token, job))
    require(len(accepted) == 6 and len(set(accepted)) == 6, "CAPACITY_TOTAL_ACCEPTED")
    require(api.sql("SELECT COUNT(*) FROM document_translation_job;"
                    "SELECT COUNT(*) FROM document_translation_batch;"
                    "SELECT COUNT(*) FROM document_translation_object_write;") == [b"6", b"0", b"0"],
            "CAPACITY_FINAL_DB_COUNTS")
    report["concurrent"]["statuses"] = [202, 503]
    checkpoint("concurrent_end")
    return report
