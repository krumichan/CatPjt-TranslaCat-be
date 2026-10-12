-- JWT 갱신 토큰 저장 길이: 기본 VARCHAR(255)는 정상 서명 토큰도 담지 못할 수 있다.
-- 운영 DB에 자동 실행하지 않는다. 대상 schema를 확인하고 유지보수 절차에 따라 적용한다.
-- 기존 nullable 정책을 유지하고 갱신 API의 8192자 입력 한도와 일치시킨다.
ALTER TABLE refresh_token MODIFY COLUMN token VARCHAR(8192) NULL;
