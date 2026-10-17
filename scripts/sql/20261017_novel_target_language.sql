-- 기존 임베디드 소설 테이블에 한 번 적용하는 추가 SQL이다. 구 writer를 중지한 뒤 신 BE와 함께 적용한다.
-- 기존 본문 작업/용어집은 한국어로 보존한다. 원문, 번역 결과, 멱등 키, 음성 행은 삭제하지 않는다.
ALTER TABLE novel_translation_job
    ADD COLUMN target_language VARCHAR(2) NOT NULL DEFAULT 'ko';

ALTER TABLE novel_glossary
    ADD COLUMN target_language VARCHAR(2) NOT NULL DEFAULT 'ko',
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (work_key, actor_id, target_language);
