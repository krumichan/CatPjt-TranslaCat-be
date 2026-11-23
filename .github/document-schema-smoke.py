"""隔離 MySQL/TiDB で文書 DDL と主な永続化制約を検証する。"""

from hashlib import sha256
import json
from pathlib import Path
import subprocess
import sys
import time

SQL_HASHES = {
    '20261122_document_translation_jobs.sql': '0eabc1a26e96369d7445673914a30430f911d3452fd8f67fa1f8a6d81d454726',
    '20261122_document_translation_object_writes.sql': 'ea6f08cbe3f3d8d515258ad8d42d12907e50488508c1d80f472f0fc0c0d673ed',
    '20261123_document_translation_multipart_uploads.sql': 'cdb8fdc9e02f90e30a13fc6993c0fddfd6d8a804d97bc1dcba33b80d06e79b20',
}


def query(container, sql, database=True, accepted=True):
    """合成コンテナ名だけを受け取り、固定 SQL を stdin 経由で送る。"""
    assert container.startswith('document-schema-')
    command = ['docker', 'exec', '-i', container, 'mysql', '--protocol=TCP', '--host=127.0.0.1',
               '--connect-timeout=2', '--user=root', '--batch', '--skip-column-names']
    if database:
        command.append('document_acceptance')
    result = subprocess.run(command, input=sql.encode(), capture_output=True, timeout=35)
    assert (result.returncode == 0) == accepted, result.stderr.decode(errors='replace')[-2500:]
    return result.stdout.decode().strip()


def insert(job, owner, request, retry=None):
    """実ユーザーに依存しない最小の合成接收行を作る。"""
    retry_value = 'NULL' if retry is None else "'" + retry + "'"
    return f"""INSERT INTO document_translation_job
      (id,owner_id,retry_of,idempotency_key,request_hash,format,file_name,source_language,target_language,
       input_sha256,input_bytes,warnings,created_at,updated_at,expires_at)
      VALUES ('{job}',{owner},{retry_value},'{request}',REPEAT('a',64),'xlsx','synthetic.xlsx','ja','en',
       REPEAT('b',64),1,'[]',NOW(6),NOW(6),DATE_ADD(NOW(6),INTERVAL 1 DAY));"""


def main():
    """再適用・生成列の排他・FK の削除動作を両エンジンで実測する。"""
    container, engine, output = sys.argv[1:]
    assert engine in ('mysql', 'tidb')
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    for attempt in range(60):
        try:
            query(container, 'SELECT 1;', database=False)
            break
        except AssertionError:
            time.sleep(2)
    else:
        raise RuntimeError('SYNTHETIC_DATABASE_UNAVAILABLE')
    query(container, 'CREATE DATABASE document_acceptance CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;', database=False)
    version = query(container, 'SELECT VERSION();')
    assert ('TiDB' in version) == (engine == 'tidb')

    scripts = []
    for name, digest in SQL_HASHES.items():
        content = (Path('scripts/sql') / name).read_bytes()
        assert sha256(content).hexdigest() == digest
        scripts.append(content.decode())
    for sql in scripts[:2]:
        query(container, sql)
    query(container, "INSERT INTO document_translation_object_write VALUES ('source-legacy-synthetic',9,'00000000-0000-4000-8000-000000000009','WRITING',NOW(6),NULL);")
    old_query = "SELECT object_key,owner_id,job_id,state,started_at,settled_at FROM document_translation_object_write ORDER BY object_key;"
    old_rows = query(container, old_query)
    query(container, scripts[2])
    assert query(container, old_query) == old_rows
    assert query(container, "SELECT COUNT(*) FROM document_translation_object_write WHERE object_key='source-legacy-synthetic' AND upload_id IS NULL;") == '1'
    for sql in scripts:
        query(container, sql)
    assert query(container, old_query) == old_rows
    assert query(container, "SELECT COLUMN_TYPE,IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='document_translation_object_write' AND column_name='upload_id';") == 'varbinary(2048)\tYES'
    assert query(container, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();") == '3'

    first = '00000000-0000-4000-8000-000000000001'
    second = '00000000-0000-4000-8000-000000000002'
    child = '00000000-0000-4000-8000-000000000003'
    query(container, insert(first, 7, first))
    query(container, insert(second, 7, second), accepted=False)
    query(container, f"UPDATE document_translation_job SET status='SUCCEEDED' WHERE id='{first}';")
    query(container, insert(second, 7, second))
    query(container, insert(child, 8, child, first))
    query(container, f"INSERT INTO document_translation_batch VALUES ('{first}',0,REPEAT('c',64),'batch-synthetic',NOW(6));")
    query(container, f"DELETE FROM document_translation_job WHERE id='{first}';")
    assert query(container, 'SELECT COUNT(*) FROM document_translation_batch;') == '0'
    assert query(container, f"SELECT COUNT(*) FROM document_translation_job WHERE id='{child}' AND retry_of IS NULL;") == '1'
    assert query(container, 'SELECT COUNT(*) FROM document_translation_job WHERE active_owner_id IS NOT NULL;') == '2'
    query(container, f"INSERT INTO document_translation_object_write (object_key,owner_id,job_id,state,started_at,settled_at,upload_id) VALUES ('source-synthetic',7,'{first}','SETTLED',NOW(6),NOW(6),'synthetic/upload+id=');")
    assert query(container, 'SELECT COUNT(*) FROM document_translation_object_write;') == '2'
    assert query(container, "SELECT CAST(upload_id AS CHAR) FROM document_translation_object_write WHERE object_key='source-synthetic';") == 'synthetic/upload+id='
    definitions = '\n'.join(query(container, 'SHOW CREATE TABLE ' + table + ';') for table in (
        'document_translation_job', 'document_translation_batch', 'document_translation_object_write'))
    (output / (engine + '-definitions.txt')).write_text(definitions)
    result = {'passed': True, 'engine': engine, 'version': version, 'migrationAppliedTwice': True,
              'activeOwnerUniqueness': True, 'terminalOwnerAllowsNewJob': True,
              'batchDeleteCascades': True, 'retryParentDeleteSetsNull': True,
              'virtualActiveOwnerColumn': True, 'objectWriteIndependentTracking': True,
              'oldWriteRowsPreserved': True, 'uploadIdNullableBinaryColumn': True, 'sqlSha256': SQL_HASHES}
    (output / (engine + '-schema-smoke.json')).write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


if __name__ == '__main__':
    main()
