-- 일반 회원가입 / 이메일 인증 / 명시적 Google 연결 (TiDB 8.5+).
-- 운영 DB에 자동 실행하지 않는다. 백업 후 대상 schema를 선택하고 유지보수 창에서 실행한다.
-- TiDB가 지원하지 않는 stored procedure는 사용하지 않는다. 적용자는 정규화 이메일과
-- 소셜 identity 중복이 0인지 read-only preflight로 먼저 확인해야 한다.

ALTER TABLE `user` ADD COLUMN IF NOT EXISTS birth_date DATE NULL;
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS email_verified_at DATETIME(6) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_google_identity ON `user` (social_type, social_id);

CREATE TABLE IF NOT EXISTS auth_email_challenge (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    user_id BIGINT NULL,
    code_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    proof_hash VARCHAR(64) NULL,
    proof_expires_at DATETIME(6) NULL,
    consumed_at DATETIME(6) NULL,
    CONSTRAINT uk_auth_email_proof UNIQUE (proof_hash),
    CONSTRAINT fk_auth_email_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    INDEX idx_auth_email_challenge_email_created (email, created_at),
    INDEX ix_auth_email_challenge_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS auth_email_quota (
    id BIGINT NOT NULL PRIMARY KEY,
    quota_date DATE NOT NULL,
    reserved_count INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

-- quota가 없으면 애플리케이션은 발송을 거부한다. 재실행해도 사용 중인 카운터는 초기화하지 않는다.
INSERT INTO auth_email_quota (id, quota_date, reserved_count)
SELECT 1, CURRENT_DATE, 0
WHERE NOT EXISTS (SELECT 1 FROM auth_email_quota WHERE id = 1);

CREATE TABLE IF NOT EXISTS auth_google_intent (
    token_hash VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    nonce_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    CONSTRAINT fk_auth_google_intent_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    INDEX ix_auth_google_intent_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS auth_reauthentication_grant (
    token_hash VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    CONSTRAINT fk_auth_reauth_grant_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    INDEX ix_auth_reauth_grant_expiry (expires_at)
) ENGINE=InnoDB;

-- 기존 회원의 비밀번호, 이메일, ID, 생년월일, 인증 여부는 추정해서 변경하지 않는다.
