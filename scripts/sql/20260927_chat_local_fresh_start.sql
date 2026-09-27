-- 로컬 Chat 테스트 데이터 전용 수동 변경이다. 자동 startup/배포 migration이 아니다.
-- 실행자는 먼저 endpoint/container 소유권, old writer 중지, 실제 FK 목록을 확인한다.
-- 확인한 로컬 translacat 연결에서 다음 session 승인값을 설정한 뒤 실행한다.
-- SET @chat_fresh_start_confirmation = 'LOCAL_CHAT_TEST_DATA';
-- MySQL DDL은 auto-commit이다. 실패 시 남은 테이블을 재확인하고 신규 CHAT 경로를 차단한다.
-- BE DB 전체, users/profile/friend/LL 테이블, migration history는 수정하지 않는다.
DELIMITER $$
CREATE PROCEDURE remove_local_chat_legacy_tables_20260927()
BEGIN
    DECLARE selected_catalog VARCHAR(64);
    DECLARE external_references INT DEFAULT 0;
    DECLARE active_triggers INT DEFAULT 0;
    DECLARE schema_views INT DEFAULT 0;
    DECLARE schema_events INT DEFAULT 0;

    -- 이름만으로 로컬성을 추정하지 않는다. 연결 소유권 확인 뒤 명시한 session 승인도 요구한다.
    SET selected_catalog = DATABASE();
    IF selected_catalog IS NULL OR selected_catalog <> 'translacat'
       OR @chat_fresh_start_confirmation IS NULL
       OR @chat_fresh_start_confirmation <> 'LOCAL_CHAT_TEST_DATA' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Chat reset requires verified local translacat and explicit session confirmation';
    END IF;

    CREATE TEMPORARY TABLE chat_fresh_start_allowlist_20260927 (
        table_name VARCHAR(64) NOT NULL PRIMARY KEY
    );
    INSERT INTO chat_fresh_start_allowlist_20260927 (table_name) VALUES
        ('chat_message_translation'), ('chat_message'), ('open_chat_ban'),
        ('open_chat_member_profile'), ('open_chat_room'), ('chat_room_member'),
        ('chat_notification'), ('chat_room_ai_activity'), ('chat_room_ai_setting'),
        ('chat_room_ai_member'), ('chat_ai_agent'), ('chat_ai_system_setting'),
        ('user_chat_language_setting'), ('chat_room');

    -- 다른 schema를 포함하여 비채팅 테이블이 이 allowlist를 참조하면 자동 연쇄 삭제하지 않는다.
    SELECT COUNT(*) INTO external_references
    FROM information_schema.KEY_COLUMN_USAGE AS dependency
    JOIN chat_fresh_start_allowlist_20260927 AS target
      ON target.table_name = dependency.REFERENCED_TABLE_NAME
    WHERE dependency.REFERENCED_TABLE_SCHEMA = selected_catalog
      AND (dependency.TABLE_SCHEMA <> selected_catalog OR dependency.TABLE_NAME NOT IN (
        'chat_message_translation', 'chat_message', 'open_chat_ban',
        'open_chat_member_profile', 'open_chat_room', 'chat_room_member',
        'chat_notification', 'chat_room_ai_activity', 'chat_room_ai_setting',
        'chat_room_ai_member', 'chat_ai_agent', 'chat_ai_system_setting',
        'user_chat_language_setting', 'chat_room'));

    SELECT COUNT(*) INTO active_triggers
    FROM information_schema.TRIGGERS AS candidate
    WHERE candidate.EVENT_OBJECT_SCHEMA = selected_catalog;

    -- 현재 확인한 로컬 schema에는 trigger/view/event가 없다. 추가됐다면 호출 관계 검토부터 한다.
    SELECT COUNT(*) INTO schema_views
    FROM information_schema.VIEWS WHERE TABLE_SCHEMA = selected_catalog;
    SELECT COUNT(*) INTO schema_events
    FROM information_schema.EVENTS WHERE EVENT_SCHEMA = selected_catalog;

    IF external_references > 0 OR active_triggers > 0 OR schema_views > 0 OR schema_events > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Chat reset blocked by external FK, trigger, view or event; audit required';
    END IF;

    -- 현재 entity/FK 그래프의 자식부터 제거한다. FOREIGN_KEY_CHECKS를 끄지 않는다.
    DROP TABLE IF EXISTS chat_message_translation;
    DROP TABLE IF EXISTS chat_message;
    DROP TABLE IF EXISTS open_chat_ban;
    DROP TABLE IF EXISTS open_chat_member_profile;
    DROP TABLE IF EXISTS open_chat_room;
    DROP TABLE IF EXISTS chat_room_member;
    DROP TABLE IF EXISTS chat_notification;
    DROP TABLE IF EXISTS chat_room_ai_activity;
    DROP TABLE IF EXISTS chat_room_ai_setting;
    DROP TABLE IF EXISTS chat_room_ai_member;
    DROP TABLE IF EXISTS chat_ai_agent;
    DROP TABLE IF EXISTS chat_ai_system_setting;
    DROP TABLE IF EXISTS user_chat_language_setting;
    DROP TABLE IF EXISTS chat_room;

    DROP TEMPORARY TABLE chat_fresh_start_allowlist_20260927;
    SET @chat_fresh_start_confirmation = NULL;
END$$
DELIMITER ;

CALL remove_local_chat_legacy_tables_20260927();
DROP PROCEDURE remove_local_chat_legacy_tables_20260927;
