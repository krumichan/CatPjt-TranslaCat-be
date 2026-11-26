package jp.co.translacat.domain.wordbook;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 단어장 항목과 기능 내부의 조회·생성 상태를 표현한다. */
final class WordbookTypes {
    private WordbookTypes() {
    }

    enum SourceKind {
        CAMERA_CAPTURE,
        SCREENSHOT_PICKER,
        SCREENSHOT_SHARE
    }

    record LanguagePair(String sourceLanguage, String targetLanguage) {
    }

    record Draft(LanguagePair languages, String sourceText, String targetText, String context, String sourceLabel) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Entry(
            UUID id,
            String sourceLanguage,
            String targetLanguage,
            String sourceText,
            String targetText,
            String context,
            String sourceLabel,
            SourceKind sourceKind,
            long version,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt) {
    }

    record CreateResult(Entry entry, boolean duplicate) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Page(List<Entry> items, String nextCursor) {
    }

    record PageAnchor(Instant createdAt, UUID id) {
    }

    record Replay(String requestHash, UUID entryId, boolean duplicate, Instant expiresAt) {
    }
}
