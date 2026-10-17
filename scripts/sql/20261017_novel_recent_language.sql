-- BE 배포 전에 기존 recent_view에 한 번 적용하는 추가 SQL이다.
-- 기존 최근 기록·제목·소유자·조회 시각과 행 식별자는 보존하고 기존 방문 언어는 한국어로 둔다.
ALTER TABLE recent_view
    ADD COLUMN title_en VARCHAR(2000) NULL,
    ADD COLUMN target_language VARCHAR(2) NOT NULL DEFAULT 'ko';
