-- 고객센터 추가 스키마. BE 배포 전에 대상 DB에 1회 적용한다.
-- 기존 테이블/데이터를 수정하지 않으며 애플리케이션은 운영에서 ddl-auto=validate를 사용한다.

CREATE TABLE IF NOT EXISTS support_notice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    important BIT(1) NOT NULL,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_support_notice_author FOREIGN KEY (author_id) REFERENCES `user` (id)
);

CREATE TABLE IF NOT EXISTS support_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    read_at DATETIME(6),
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_support_message_recipient_id (recipient_id, id),
    CONSTRAINT fk_support_message_sender FOREIGN KEY (sender_id) REFERENCES `user` (id),
    CONSTRAINT fk_support_message_recipient FOREIGN KEY (recipient_id) REFERENCES `user` (id)
);

CREATE TABLE IF NOT EXISTS support_inquiry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reply_count INT NOT NULL DEFAULT 0,
    related_message_id BIGINT,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_support_inquiry_user_updated (user_id, updated_at),
    INDEX idx_support_inquiry_status_updated (status, updated_at),
    CONSTRAINT fk_support_inquiry_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_support_inquiry_message FOREIGN KEY (related_message_id) REFERENCES support_message (id)
);

CREATE TABLE IF NOT EXISTS support_reply (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inquiry_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    author_role VARCHAR(20) NOT NULL,
    parent_id BIGINT,
    depth INT NOT NULL,
    content TEXT NOT NULL,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_support_reply_inquiry_id (inquiry_id, id),
    CONSTRAINT fk_support_reply_inquiry FOREIGN KEY (inquiry_id) REFERENCES support_inquiry (id),
    CONSTRAINT fk_support_reply_author FOREIGN KEY (author_id) REFERENCES `user` (id),
    CONSTRAINT fk_support_reply_parent FOREIGN KEY (parent_id) REFERENCES support_reply (id)
);

CREATE TABLE IF NOT EXISTS support_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reply_id BIGINT,
    title VARCHAR(200) NOT NULL,
    read_at DATETIME(6),
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_support_notification_user_read (recipient_id, read_at, id),
    INDEX idx_support_notification_target (target_type, target_id),
    CONSTRAINT fk_support_notification_recipient FOREIGN KEY (recipient_id) REFERENCES `user` (id)
);
