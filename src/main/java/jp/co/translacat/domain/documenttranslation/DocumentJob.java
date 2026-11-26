package jp.co.translacat.domain.documenttranslation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 문서 작업 메타데이터와, 임시 작업 또는 레거시 정리를 식별하는 저장 참조다. */
record DocumentJob(
        UUID id, long ownerId, UUID retryOf, UUID idempotencyKey, String requestHash,
        String format, String fileName, String sourceLanguage, String targetLanguage,
        State state, String inputKey, String inputHash, long inputBytes, String outputKey,
        Long outputBytes, String outputHash, Integer totalSegments, int completedSegments,
        List<Warning> warnings, String errorCode, String modelRevision, UUID leaseToken,
        int recoveryCount, Instant createdAt, Instant expiresAt) {

    enum State {
        QUEUED, VALIDATING, TRANSLATING, REPACKING, CANCELLING, SUCCEEDED, FAILED, CANCELLED, EXPIRED
    }

    record Warning(String code, int count, List<String> locations) {}
}
