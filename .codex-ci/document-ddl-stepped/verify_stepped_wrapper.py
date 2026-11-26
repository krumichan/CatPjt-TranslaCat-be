"""固定 DDL ラッパーを CI 専用の TiDB 8.5.8 で検証する。"""
import hashlib
import importlib.util
import json
import os
import re
import time
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parent
RESULTS = ROOT / "results"
PORT = 14000
TABLES = ("document_translation_job", "document_translation_batch", "document_translation_object_write")


def require(condition, message):
    """CI の最適化設定に依存せず、条件違反を失敗として返す。"""
    if not condition:
        raise ValueError(message)


def connect(database=None):
    """固定ループバックで、作成済みの合成コンテナにだけ接続する。"""
    connection = pymysql.connect(
        host="127.0.0.1",
        port=PORT,
        user="root",
        password="",
        database=database,
        autocommit=True,
        connect_timeout=4,
        read_timeout=8,
        write_timeout=8,
    )
    with connection.cursor() as cursor:
        cursor.execute("SELECT VERSION(), @@port")
        version, port = cursor.fetchone()
    if not str(version).endswith("TiDB-v8.5.8") or port != 4000:
        connection.close()
        raise ValueError("Unexpected synthetic database engine or container port")
    return connection


def load_wrapper():
    """実際のラッパー本体を変更せず CI から呼ぶ。"""
    asset = ROOT / "document-ddl-stepped.py"
    require(
        hashlib.sha256(asset.read_bytes()).hexdigest() ==
        "2ce0e82cd9db260a8071427718f5b7e934f08361ea5381f37558b4245a772989",
        "Unexpected stepped wrapper bytes",
    )
    module = importlib.util.spec_from_file_location("document_ddl_stepped", asset)
    wrapper = importlib.util.module_from_spec(module)
    module.loader.exec_module(wrapper)
    # 公開 CI では実行に必要な契約フィールドだけを固定する。
    wrapper.CONTRACT_SHA = "3d3a283f9ee3b683833302825c440afc5e3ab322da78af4940ec6201c5e1f5c0"
    return wrapper


class SyntheticAdapter:
    """一つの接続を保持し、合成環境で ACK 境界を注入する。"""

    def __init__(self, connection, identity, ddl, read_only_queries, lose_at=None):
        self.connection = connection
        self.identity = identity
        self.allowed_steps = ddl
        self.read_only_queries = set(read_only_queries)
        self.lose_at = lose_at
        self.sent = []

    def identity_sha256(self):
        """選択済み schema に結び付けられた合成実行識別子を返す。"""
        return self.identity

    def set_session_lock_wait_timeout(self, seconds):
        """ラッパーに許すセッション変更を上限設定一つに限定する。"""
        require(seconds == 5, "Unexpected lock timeout")
        with self.connection.cursor() as cursor:
            cursor.execute("SET SESSION lock_wait_timeout = 5")
            cursor.execute("SET SESSION foreign_key_checks = 1")

    def select(self, statement):
        """契約の読み取り SQL だけを同じセッションで実行する。"""
        require(statement in self.read_only_queries, "Unexpected wrapper read")
        with self.connection.cursor() as cursor:
            cursor.execute(statement)
            return cursor.fetchall()

    def non_target_schema_fingerprint(self):
        """合成 user と sentinel の構造を別途比較する。"""
        tables = ("user", "sentinel")
        readback = []
        with self.connection.cursor() as cursor:
            for table in tables:
                cursor.execute(f"SHOW CREATE TABLE {chr(96)}{table}{chr(96)}")
                readback.append(cursor.fetchone()[1])
        return hashlib.sha256(json.dumps(readback, separators=(",", ":")).encode()).hexdigest()

    def execute_ddl(self, statement):
        """順序どおり一文を送信し、合図を失うケースで接続を閉じる。"""
        index = len(self.sent)
        require(statement == self.allowed_steps[index], "Unexpected wrapper DDL order")
        with self.connection.cursor() as cursor:
            self.sent.append(index + 1)
            cursor.execute(statement)

        # DDL は実エンジンで確定するが、呼び出し元には ACK を返さない。
        if self.lose_at == index + 1:
            self.connection.close()
            raise RuntimeError("Synthetic ACK lost at adapter return boundary")


def journal_to(path):
    """不明状態と確認済み状態を送信順に fsync して保存する。"""
    def append(record):
        with path.open("a", encoding="utf-8", newline="\n") as handle:
            handle.write(json.dumps(record, sort_keys=True) + "\n")
            handle.flush()
            os.fsync(handle.fileno())

    return append


def seed(connection, database):
    """CI 容器内に、保全すべき構造と行を作成する。"""
    require(re.fullmatch(r"ddl_qa_[a-z_]+", database), "Unexpected CI schema")
    quote = chr(96)
    with connection.cursor() as cursor:
        cursor.execute(f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        cursor.execute(
            f"CREATE TABLE {database}.{quote}user{quote} (id bigint NOT NULL PRIMARY KEY, "
            "account_state varchar(24) NOT NULL) ENGINE=InnoDB"
        )
        cursor.execute(f"INSERT INTO {database}.{quote}user{quote} VALUES (73011, 'ACTIVE')")
        cursor.execute(f"CREATE TABLE {database}.sentinel (id int PRIMARY KEY, item varchar(32)) ENGINE=InnoDB")
        cursor.execute(f"INSERT INTO {database}.sentinel VALUES (17, 'retained')")


def inspect_from_fresh_connection(wrapper, database, expected, ddl, readback, empty, fingerprint):
    """失敗時も新しい読み取り接続で、確定した schema と保全データを確認する。"""
    with connect(database) as connection:
        identity = hashlib.sha256(database.encode()).hexdigest()
        adapter = SyntheticAdapter(connection, identity, ddl, readback + empty)
        observed = wrapper.snapshot(adapter, readback)
        require(observed["nonTargetFingerprint"] == fingerprint, "Non-target schema drift")
        require(wrapper.verify_schema(observed, expected, alter_applied=True), "Fresh schema verification")
        require(not any(adapter.select(query) for query in empty), "Unexpected target data")
        quote = chr(96)
        with connection.cursor() as cursor:
            cursor.execute(f"SELECT id, account_state FROM {quote}user{quote}")
            require(cursor.fetchall() == ((73011, "ACTIVE"),), "Account fixture changed")
            cursor.execute("SELECT id, item FROM sentinel")
            require(cursor.fetchall() == ((17, "retained"),), "Sentinel fixture changed")


def exercise(wrapper, setup, name, lose_at=None):
    """通常、ACK 消失、再適用拒否を TiDB で照合する。"""
    expected, ddl, readback, empty = wrapper.plan()
    database = "ddl_qa_" + name
    seed(setup, database)
    identity = hashlib.sha256(database.encode()).hexdigest()
    path = RESULTS / f"{name}-journal.jsonl"

    connection = connect(database)
    try:
        db = SyntheticAdapter(connection, identity, ddl, readback + empty, lose_at)
        account = db.select(readback[3])
        fingerprint = db.non_target_schema_fingerprint()
        result = wrapper.run_once(
            db,
            expected_identity=identity,
            expected_non_target_sha=fingerprint,
            expected_account_rows=account,
            journal=journal_to(path),
        )
        sent = db.sent
    finally:
        if connection.open:
            connection.close()

    history = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    if lose_at is None:
        require(result["complete"] and result["attempted"] == result["acknowledged"] == 7,
                f"First install failed: {result['code']}")
        require(sent == list(range(1, 8)), "Unexpected normal DDL order")
    else:
        require(not result["complete"] and result["code"] == "DOCUMENT_DDL_STOPPED",
                f"Unexpected ACK result: {result['code']}")
        require(result["attempted"] == 6 and result["acknowledged"] == 5
                and result["uncertainDdlStep"] == 6 and result["reconcileWithFreshReadOnly"],
                "ACK-loss reconciliation record differs")
        require(sent == list(range(1, 7)), "A later DDL was sent after the uncertain ALTER")
        require(history[-2]["uncertainStatement"] == 6 and history[-2]["acknowledged"] == 5,
                "Uncertain state was not persisted")

    inspect_from_fresh_connection(wrapper, database, expected, ddl, readback, empty, fingerprint)
    with connect(database) as reopened:
        duplicate = SyntheticAdapter(reopened, identity, ddl, readback + empty)
        rejected = wrapper.run_once(
            duplicate,
            expected_identity=identity,
            expected_non_target_sha=fingerprint,
            expected_account_rows=account,
            journal=journal_to(RESULTS / f"{name}-reapply.jsonl"),
        )
        require(rejected["code"] == "DOCUMENT_DDL_PREFLIGHT_DRIFT"
                and rejected["attempted"] == 0 and not duplicate.sent, "Unexpected reapplication")

    return {
        "report": result,
        "statementsSent": sent,
        "freshSchemaReadbackPassed": True,
        "syntheticAccountsAndSentinelPreserved": True,
        "reapplicationRejectedBeforeSend": True,
    }


def main():
    """合成 CI の状態のみを永続化する。本番接続を受け取る入口はない。"""
    require(os.environ.get("GITHUB_ACTIONS") == "true", "CI isolation is required")
    RESULTS.mkdir(exist_ok=False)
    wrapper = load_wrapper()

    for attempt in range(35):
        try:
            setup = connect()
        except pymysql.err.OperationalError:
            if attempt == 34:
                raise
            time.sleep(2)
        else:
            break

    with setup:
        normal = exercise(wrapper, setup, "first_install")
        lost = exercise(wrapper, setup, "lost_ack", lose_at=6)

    summary = {
        "engine": "TiDB 8.5.8",
        "passed": True,
        "driver": "PyMySQL 1.2.4, synthetic CI only",
        "ackFaultBoundary": "server statement completed before the synthetic adapter withheld acknowledgment",
        "productionConnectionMade": False,
        "cases": {"firstInstall": normal, "lostAlterAck": lost},
    }
    (RESULTS / "stepped-wrapper-result.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary))


if __name__ == "__main__":
    main()
