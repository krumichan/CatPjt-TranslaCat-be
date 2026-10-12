-- 20261011_support_center.sql 적용 후 실행한다.
-- 기존 support_notice.important 값은 false=NORMAL, true=URGENT로 그대로 사용한다.
-- 기존 공지와 알림을 수정하지 않고 계정별 영구 숨김 이력만 추가한다.

CREATE TABLE IF NOT EXISTS support_notice_dismissal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notice_id BIGINT NOT NULL,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_support_notice_dismissal_user_notice (user_id, notice_id),
    INDEX idx_support_notice_dismissal_notice (notice_id),
    CONSTRAINT fk_support_notice_dismissal_user
        FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    CONSTRAINT fk_support_notice_dismissal_notice
        FOREIGN KEY (notice_id) REFERENCES support_notice (id) ON DELETE CASCADE
);
