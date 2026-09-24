-- BE Core DB의 기존 Listening outbox에 새 event_type을 수용한다.
-- MySQL의 기존 event_type이 ENUM인 배포에도 적용할 수 있도록 문자열 컬럼으로 명시한다.
-- 데이터 삭제/설정 테이블 변경은 없다. 먼저 로컬/스테이징에서 검증하고 BE 전환 전에 적용한다.
ALTER TABLE language_learning_listening_outbox
    MODIFY COLUMN event_type VARCHAR(40) NOT NULL;
