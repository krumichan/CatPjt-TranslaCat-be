-- 다중 브라우저·Android·iOS 로그인을 위한 새 세션 저장소 (TiDB 8.5+).
-- 운영 DB에 자동 실행하지 않는다. 배포 담당자가 새 BE 배포 전에 적용한다.
-- 기존 refresh_token 행과 사용자 계정은 만료까지 호환하므로 변경·복사·삭제하지 않는다.
CREATE TABLE IF NOT EXISTS auth_session (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    INDEX ix_auth_session_user (user_id),
    INDEX ix_auth_session_expiry (expires_at)
) ENGINE=InnoDB;

-- 새 로그인은 독립 sid와 refresh SHA-256만 이 테이블에 저장한다.
-- 기존 테이블의 원문 refresh를 이 테이블로 이관하거나 SQL 결과로 출력하지 않는다.
