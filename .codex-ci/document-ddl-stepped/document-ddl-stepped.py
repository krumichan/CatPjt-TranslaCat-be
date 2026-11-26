"""既存の承認済み DB セッション用。接続、資格、実行入口はこのモジュールにない。"""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CONTRACT_SHA = "fb76a98617bc42d6480bfdc435e978a2379e171afa675143523688a7209a8d8f"
MUTATION_STEPS = {1, 2, 3, 6}
TARGETS = ("document_translation_job", "document_translation_batch", "document_translation_object_write")
SHA = re.compile(r"[a-f0-9]{64}")


# 許可する SQL は固定契約と同じ生バイトだけにする。
def read_pinned(path, expected):
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError("DOCUMENT_DDL_INPUT_PIN")
    return data


# 固定 SQL の statement 順と単一セッション境界を保つ。
def statements(payload):
    return [
        part.strip() for part in re.sub(r"(?m)^\s*--[^\r\n]*", "", payload.decode()).split(";")
        if part.strip()
    ]


# writable SQL と catalog の読み取りに別の入口を持つ。
def plan():
    contract = json.loads(read_pinned(ROOT / "document-db-contract.json", CONTRACT_SHA))
    ddl = []
    for row in contract["migrationFiles"]:
        path = ROOT / row["path"]
        if path.parent != ROOT / "sql" or path.resolve(strict=True) != path:
            raise ValueError("DOCUMENT_DDL_PATH")
        parts = statements(read_pinned(path, row["sha256"]))
        if len(parts) != row["statements"]:
            raise ValueError("DOCUMENT_DDL_COUNT")
        ddl.extend(parts)
    if len(ddl) != 7:
        raise ValueError("DOCUMENT_DDL_COUNT")
    queries = []
    for leaf, field in (("document-schema-readback.sql", "readback"), ("document-schema-empty.sql", "verifyEmpty")):
        selected = statements(read_pinned(ROOT / leaf, contract[field]["sha256"]))
        if not all(query.startswith("SELECT ") for query in selected):
            raise ValueError("DOCUMENT_DDL_READ_SCOPE")
        queries.append(selected)
    return contract["expectedTables"], ddl, *queries


# ドライバーの表示差を揃え、DDL が指定する型・式の意味は維持する。
def column_type(value):
    return re.sub(r"^(bigint|int)\(\d+\)$", r"\1", str(value).lower())


# MySQL の generated CASE の表示にだけ挿入される括弧・型指定を除く。
def generated(value):
    value = re.sub(r"_[A-Za-z0-9_]+(?=')", "", str(value))
    return re.sub(r"[\s\`()]", "", value).lower()


# DB adapter は SELECT だけ可能なメソッドを検査に渡す。
def snapshot(db, readback):
    values = [db.select(query) for query in readback]
    if len(values) != 8 or tuple(map(len, values[:2])) != (1, 1):
        raise ValueError("DOCUMENT_DDL_READBACK_SHAPE")
    environment, defaults, tables, account, columns, indexes, constraints, foreign = (
        values[0][0], values[1][0], *values[2:],
    )
    return {"environment": environment, "defaults": defaults, "tables": tables, "account": account,
            "columns": columns, "indexes": indexes, "constraints": constraints, "foreign": foreign,
            "nonTargetFingerprint": db.non_target_schema_fingerprint()}


# 名称が同じだけでは成功にせず、列・優先順・FK・削除規則を比較する。
def verify_schema(state, expected, *, alter_applied):
    schema = state["environment"][0]
    wanted_collation = state["defaults"][1]
    tables = {row[0]: tuple(row[1:]) for row in state["tables"] if row[0] in TARGETS}
    if set(tables) != set(expected):
        return False
    for name, spec in expected.items():
        if tables[name] != ("BASE TABLE", "InnoDB", wanted_collation):
            return False
        want_columns = [col for col in spec["columns"] if alter_applied or col["name"] != "upload_id"]
        found = sorted((row for row in state["columns"] if row[0] == name), key=lambda row: int(row[1]))
        if len(found) != len(want_columns):
            return False
        for order, (row, col) in enumerate(zip(found, want_columns), 1):
            _, ordinal, col_name, db_type, null, default, extra, expression, charset, collation = row
            if (int(ordinal), col_name, column_type(db_type), null == "YES", default) != (
                    order, col["name"], col["columnType"], col["nullable"], col["default"]):
                return False
            if col.get("generated"):
                if "VIRTUAL" not in extra or generated(expression) != generated(col["generationExpression"]):
                    return False
            elif expression or extra:
                return False
            text_type = col["columnType"].startswith(("char(", "varchar("))
            if text_type and (charset, collation) != tuple(state["defaults"]):
                return False

        target_indexes = spec["indexes"]
        actual = [row for row in state["indexes"] if row[0] == name]
        allowed_indexes = {
            (key, 0 if idx["unique"] else 1, seq, col, "A", None, "BTREE", "YES")
            for key, idx in target_indexes.items() for seq, col in enumerate(idx["columns"], 1)
        }
        got_indexes = {(key, int(nonunique), int(seq), proto, order, prefix, kind, visible)
                       for _, key, nonunique, seq, proto, order, prefix, kind, visible in actual}
        if got_indexes != allowed_indexes or len(actual) != len(got_indexes):
            return False
        target_constraints = {
            (key, "PRIMARY KEY" if key == "PRIMARY" else "UNIQUE")
            for key, idx in target_indexes.items() if idx["unique"]
        } | {(key, "FOREIGN KEY") for key in spec["foreignKeys"]}
        if {(row[1], row[2]) for row in state["constraints"] if row[0] == name} != target_constraints:
            return False

        actual_foreign = [row for row in state["foreign"] if row[0] == name]
        if len(actual_foreign) != len(spec["foreignKeys"]):
            return False
        for _, key, ordinal, col, ref_schema, ref_table, ref_col, update, delete in actual_foreign:
            want = spec["foreignKeys"].get(key)
            if (want is None or int(ordinal) != 1 or [col] != want["columns"] or ref_schema != schema
                    or ref_table != want["referencedTable"] or [ref_col] != want["referencedColumns"]
                    or update not in want["onUpdateDefault"] or delete != want["onDelete"]):
                return False
    return True


# 本当に変更された箇所を突き止めるため、途中の target 帳簿も照合する。
def expected_at(completed, schema):
    prefixes = {0: 0, 1: 1, 2: 2, 3: 3, 4: 3, 5: 3, 6: 3, 7: 3}
    return {name: schema[name] for name in TARGETS[:prefixes[completed]]}


# 現在の endpoint と catalog は担当者の実運用ゲートから注入する。旧 SHA を使わない。
def run_once(db, *, expected_identity, expected_non_target_sha, expected_account_rows, journal):
    """Existing runner must own the DDL lock and these callbacks. No automatic resume or reconnect."""
    expected, ddl, readback, empty = plan()
    report = {"complete": False, "attempted": 0, "acknowledged": 0,
              "uncertainStatement": None, "uncertainDdlStep": None,
              "lastAcknowledgedSchemaChange": None,
              "existingSchemaChanged": None, "businessDmlExecuted": 0}
    if not (SHA.fullmatch(expected_identity) and SHA.fullmatch(expected_non_target_sha)):
        raise ValueError("DOCUMENT_DDL_OWNER_GATE")
    try:
        if db.identity_sha256() != expected_identity:
            raise ValueError("DOCUMENT_DDL_ENDPOINT")
        # この API は既存 adapter が同じ connection にだけ適用する。global 値は変更しない。
        db.set_session_lock_wait_timeout(5)
        before = snapshot(db, readback)
        env = before["environment"]
        if (not env[0] or env[1] != "InnoDB" or str(env[2]) != "1" or str(env[3]) != "5"
                or list(map(tuple, before["account"])) != list(map(tuple, expected_account_rows))
                or {row[0] for row in before["account"]} != {"id", "account_state"}
                or before["nonTargetFingerprint"] != expected_non_target_sha
                or not verify_schema(before, {}, alter_applied=False)):
            raise ValueError("DOCUMENT_DDL_PREFLIGHT_DRIFT")

        for step, statement in enumerate(ddl, 1):
            # 송신 전 불명 상태를 먼저 남겨 프로세스 중단도 재적용으로 이어지지 않게 한다.
            report.update(attempted=step, uncertainStatement=step,
                          uncertainDdlStep=step if step in MUTATION_STEPS else None)
            journal(dict(report))
            db.execute_ddl(statement)
            report.update(acknowledged=step, uncertainStatement=None, uncertainDdlStep=None)
            if step in MUTATION_STEPS:
                report["lastAcknowledgedSchemaChange"] = step
            journal(dict(report))
            if step in MUTATION_STEPS:
                current = snapshot(db, readback)
                report["existingSchemaChanged"] = current["nonTargetFingerprint"] != expected_non_target_sha
                if (report["existingSchemaChanged"]
                        or not verify_schema(current, expected_at(step, expected), alter_applied=step == 6)):
                    raise ValueError("DOCUMENT_DDL_READBACK_DRIFT")
        after = snapshot(db, readback)
        report["existingSchemaChanged"] = after["nonTargetFingerprint"] != expected_non_target_sha
        if (report["existingSchemaChanged"] or not verify_schema(after, expected, alter_applied=True)
                or any(db.select(query) for query in empty)):
            raise ValueError("DOCUMENT_DDL_FINAL_VERIFY")
        report.update(complete=True, code="DOCUMENT_DDL_COMPLETE")
    except Exception as fault:
        # 例外内容や資格情報を証拠に出さず、担当者の別の read-only reconciliation に渡す。
        token = str(fault)
        code = token if isinstance(fault, ValueError) and re.fullmatch(r"DOCUMENT_DDL_[A-Z_]+", token) else "DOCUMENT_DDL_STOPPED"
        report.update(code=code, reconcileWithFreshReadOnly=report["attempted"] > 0)
    journal(dict(report))
    return report
