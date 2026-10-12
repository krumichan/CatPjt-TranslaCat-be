-- 일반 회원가입 / 이메일 인증 / 명시적 Google 연결 (MySQL 8).
-- 운영 DB에 자동 실행하지 않는다. 백업 후 대상 schema를 선택하고 유지보수 창에서 실행한다.
-- DDL은 자동 커밋된다. 사전 검사 실패 시 기존 계정을 임의로 병합하거나 삭제하지 않는다.

DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_local_auth_20261011$$
CREATE PROCEDURE migrate_local_auth_20261011()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the target database first';
    END IF;
    IF EXISTS (
        SELECT 1 FROM `user` GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate normalized emails require manual account review';
    END IF;
    IF EXISTS (
        SELECT 1 FROM `user` WHERE social_id IS NOT NULL
        GROUP BY social_type, social_id HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate social identities require manual account review';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'birth_date') THEN
        ALTER TABLE `user` ADD COLUMN birth_date DATE NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'email_verified_at') THEN
        ALTER TABLE `user` ADD COLUMN email_verified_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'uk_user_google_identity') THEN
        ALTER TABLE `user` ADD CONSTRAINT uk_user_google_identity UNIQUE (social_type, social_id);
    END IF;
END$$
DELIMITER ;

CALL migrate_local_auth_20261011();
DROP PROCEDURE migrate_local_auth_20261011;

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
SELECT 1, UTC_DATE(), 0
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
