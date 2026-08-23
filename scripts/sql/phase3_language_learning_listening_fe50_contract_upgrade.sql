-- FE #50 integration contract upgrade for BE #48
-- MySQL 8.x. Apply once after the existing language_learning_user_setting table exists.

ALTER TABLE language_learning_user_setting
    ADD COLUMN daily_listening_goal_count INT NOT NULL DEFAULT 5
        AFTER daily_speaking_goal_minutes,
    ADD COLUMN default_listening_task_types VARCHAR(200) NOT NULL
        DEFAULT '["DICTATION"]' AFTER daily_listening_goal_count,
    ADD COLUMN pending_daily_listening_goal_count INT NULL
        AFTER pending_daily_speaking_goal_minutes;

-- Existing users start with the Phase 3 defaults. Future changes to the daily
-- Listening goal are promoted on pending_effective_date; the default Task
-- selection is a Session preference and is effective for the next Session.
UPDATE language_learning_user_setting
SET daily_listening_goal_count = 5,
    default_listening_task_types = '["DICTATION"]'
WHERE daily_listening_goal_count IS NULL
   OR default_listening_task_types IS NULL
   OR default_listening_task_types = '';
