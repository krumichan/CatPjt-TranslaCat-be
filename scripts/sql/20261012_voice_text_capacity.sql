-- Voice 원문/번역/읽기 토큰은 UTF-8의 255바이트를 쉽게 초과한다.
-- 운영 DB에 자동 실행하지 않는다. 대상 schema를 확인하고 유지보수 절차에 따라 적용한다.
-- 기존 데이터와 nullable 정책을 보존하며 @Lob의 MySQL TINYTEXT 기본 매핑을 확장한다.
ALTER TABLE voice_segment
    MODIFY COLUMN source_text LONGTEXT NULL,
    MODIFY COLUMN translated_text LONGTEXT NULL,
    MODIFY COLUMN source_reading_tokens LONGTEXT NULL;

ALTER TABLE voice_session
    MODIFY COLUMN policy_snapshot LONGTEXT NOT NULL;
