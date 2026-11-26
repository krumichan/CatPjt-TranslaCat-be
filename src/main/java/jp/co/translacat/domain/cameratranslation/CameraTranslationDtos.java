package jp.co.translacat.domain.cameratranslation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 카메라 번역 HTTP 경계에서 사용하는 요청과 결과 값. */
final class CameraTranslationDtos {
    private CameraTranslationDtos() {
    }

    record TranslationRequest(UUID requestId, Mode mode, String sourceLanguage, String targetLanguage,
            List<Segment> segments) {
    }

    enum Mode {
        STILL, PREVIEW
    }

    record Segment(String id, String text, String context) {
    }

    record TranslationResult(UUID requestId, List<SegmentResult> segments) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record SegmentResult(String id, SegmentStatus status, String translatedText, String errorCode) {
    }

    enum SegmentStatus {
        TRANSLATED, FAILED
    }

    enum JobStatus {
        QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELED
    }

    record Accepted(UUID jobId, JobStatus status, Instant expiresAt) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record JobView(UUID jobId, JobStatus status, Instant expiresAt, JsonNode result, String errorCode) {
    }
}
