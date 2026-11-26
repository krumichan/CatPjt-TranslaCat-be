-- 세 테이블이 완성 스키마와 일치하고 문서 접수가 비활성일 때만 실행.
SELECT 'document_translation_job' AS nonempty_table
FROM document_translation_job LIMIT 1;
SELECT 'document_translation_batch' AS nonempty_table
FROM document_translation_batch LIMIT 1;
SELECT 'document_translation_object_write' AS nonempty_table
FROM document_translation_object_write LIMIT 1;
