-- 기존 웹 Google 인증에서 native 앱으로 복귀하는 PKCE 일회용 요청 (TiDB 8.5+).
-- 운영 DB에 자동 적용하지 않는다. auth_session migration 뒤, 새 BE 배포 전에 적용한다.
CREATE TABLE IF NOT EXISTS auth_mobile_request (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    code_challenge VARCHAR(43) NOT NULL,
    state VARCHAR(128) NOT NULL,
    redirect_uri VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    approved_user_id BIGINT NULL,
    approved_at DATETIME(6) NULL,
    code_hash VARCHAR(64) NULL,
    code_expires_at DATETIME(6) NULL,
    consumed_at DATETIME(6) NULL,
    CONSTRAINT fk_auth_mobile_approved_user FOREIGN KEY (approved_user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    INDEX ix_auth_mobile_request_expiry (expires_at),
    INDEX ix_auth_mobile_code_expiry (code_expires_at)
) ENGINE=InnoDB;

-- code/verifier/Google idToken/사용자 세션 토큰 원문은 이 테이블에 저장하지 않는다.
-- 승인한 code는 SHA-256만 저장하며 만료 요청은 앱의 주기 작업이 소량씩 제거한다.
