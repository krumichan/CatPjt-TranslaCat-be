-- 기존 계정의 값은 NULL로 유지한다. 적용 전 information_schema에서 동일 컬럼의 존재·형식을 확인한다.
ALTER TABLE `user` ADD COLUMN `password_reset_at` DATETIME(6) NULL;
