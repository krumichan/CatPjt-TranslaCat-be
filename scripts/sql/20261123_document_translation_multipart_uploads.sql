-- 기존 로컬 객체와 업로드 ID가 없던 기록을 보존하고, 새 원격 쓰기에만 ID를 저장한다.
SET @document_multipart_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'document_translation_object_write'
            AND column_name = 'upload_id'
    ),
    'SELECT 1',
    'ALTER TABLE document_translation_object_write ADD COLUMN upload_id VARBINARY(2048) NULL'
);
PREPARE document_multipart_stmt FROM @document_multipart_ddl;
EXECUTE document_multipart_stmt;
DEALLOCATE PREPARE document_multipart_stmt;
