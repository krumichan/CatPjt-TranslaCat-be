-- 새 기기 등록과 영속 generation/tombstone. 기존 사용자·auth_session·refresh 행은 수정하지 않는다.
-- 운영 자동 실행 없음: 배포 담당자가 검증한 대상에 새 BE 배포 전에 별도로 적용한다.
CREATE TABLE IF NOT EXISTS push_registry_lock (
    id INT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO push_registry_lock (id)
SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM push_registry_lock WHERE id = 1);

CREATE TABLE IF NOT EXISTS push_installation (
    installation_id VARCHAR(36) NOT NULL PRIMARY KEY,
    proof_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    account_binding_id VARCHAR(36) NOT NULL,
    generation BIGINT NOT NULL,
    token_version BIGINT NOT NULL,
    last_mutation_hash VARCHAR(64) NOT NULL,
    provider VARCHAR(16) NULL,
    expo_push_token VARCHAR(280) NULL,
    token_key VARCHAR(64) NULL,
    platform VARCHAR(8) NULL,
    app_environment VARCHAR(16) NULL,
    project_id VARCHAR(36) NULL,
    enabled BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_push_installation_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    CONSTRAINT uq_push_installation_token UNIQUE (token_key),
    INDEX ix_push_installation_user (user_id, enabled)
) ENGINE=InnoDB;

-- installationProof 원문은 저장하지 않는다. DELETE도 행을 남겨 늦은 낮은 generation을 거절한다.
-- sid FK를 두지 않아 만료 세션 정리가 tombstone을 지우지 않게 하며, target 조회가 live auth_session을 join한다.
-- 짧은 변경 transaction의 락 순서는 registry 고정행 → 현재 auth_session이다. 기존 logout은 sid만 잠근다.
