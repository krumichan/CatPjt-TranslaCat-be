-- Apply before deploying account-deletion-aware novel writers. No existing user content is modified here.
CREATE TABLE IF NOT EXISTS novel_account_deletion_audio (
  operation_id CHAR(36) NOT NULL,
  identity_hash CHAR(64) NOT NULL,
  max_attempt INT NOT NULL,
  delete_after BIGINT NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (operation_id, identity_hash)
);
CREATE TABLE IF NOT EXISTS novel_audio_object_write (
  identity_hash CHAR(64) NOT NULL,
  attempt INT NOT NULL,
  user_id BIGINT NOT NULL,
  state VARCHAR(16) NOT NULL,
  PRIMARY KEY (identity_hash, attempt),
  INDEX ix_novel_audio_object_write_user (user_id, state)
);
