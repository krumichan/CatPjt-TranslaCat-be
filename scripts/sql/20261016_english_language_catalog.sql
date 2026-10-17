-- Core BE의 기존 language(code VARCHAR(2) PK, name VARCHAR(50)) 테이블용 수동 seed다.
-- 대상 schema를 확인한 뒤 적용한다. 애플리케이션 시작이나 배포에서 자동 실행하지 않는다.
-- 기존 ko/ja/기타 행과 이미 등록된 영어 표시명은 변경하지 않는다.
INSERT INTO language (code, name)
SELECT 'en', 'English'
WHERE NOT EXISTS (
    SELECT 1 FROM language WHERE LOWER(code) = 'en'
);
