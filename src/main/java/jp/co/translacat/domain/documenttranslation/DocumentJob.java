package jp.co.translacat.domain.documenttranslation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 문서 번역 모듈에서 보관하는 작업과 파일 식별자다. */
record DocumentJob(
        UUID id, long ownerId, UUID retryOf, UUID idempotencyKey, String requestHash,
        String format, String fileName, String sourceLanguage, String targetLanguage,
        State state, String inputKey, String inputHash, long inputBytes, String outputKey,
        Long outputBytes, String outputHash, Integer totalSegments, int completedSegments,
        List<Warning> warnings, String errorCode, String modelRevision, UUID leaseToken,
        int recoveryCount, Instant createdAt, Instant expiresAt) {

    enum State {
        QUEUED, VALIDATING, TRANSLATING, REPACKING, CANCELLING, SUCCEEDED, FAILED, CANCELLED, EXPIRED;

        // 원본이 유효할 때 새 시도를 만들 수 있는 종료 상태인지 확인한다.
        boolean permitsRetry(String errorCode) {
            return this == CANCELLED || this == FAILED && errorCode != null && switch (errorCode) {
                case "TRANSLATION_UNAVAILABLE", "TRANSLATION_TIMEOUT", "WORKER_UNAVAILABLE" -> true;
                default -> false;
            };
        }
    }

    record Warning(String code, int count, List<String> locations) {}
}
