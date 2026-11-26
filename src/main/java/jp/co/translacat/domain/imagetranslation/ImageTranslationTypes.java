package jp.co.translacat.domain.imagetranslation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ImageTranslationTypes {
    static final int MAX_PAGES = 100;
    static final int MIB = 1024 * 1024;
    static final int MAX_IMAGE_BYTES = 15 * MIB;
    static final int MAX_UPLOAD_BYTES = 100 * MIB;
    static final int MAX_EXPANDED_BYTES = 200 * MIB;
    static final int MAX_ENTRY_COUNT = 300;
    static final long MAX_EPISODE_PIXELS = 300_000_000L;

    enum SourceKind {
        FILES, ZIP, LINK
    }

    enum JobStatus {
        AWAITING_UPLOAD, PREPARING, READY, QUEUED, RUNNING, SUCCEEDED, PARTIAL_FAILED, FAILED, CANCELLED, EXPIRED
    }

    enum PageState {
        PENDING, RUNNING, READY, NO_TEXT, FAILED, CANCELLED
    }

    enum Stage {
        ACQUIRE, VALIDATE, OCR, TRANSLATE, STORE, RENDER
    }

    enum Operation {
        CREATE, PREPARE, START, CANCEL, RETRY
    }

    record CreateRequest(String sourceLanguage, String targetLanguage, SourceRequest source) {}

    record SourceRequest(SourceKind kind, List<FileRequest> files, FileRequest archive, String url) {}

    record FileRequest(String clientFileId, String fileName, String contentType, long sizeBytes) {}

    record Selection(List<UUID> pageIds, long revision) {}

    record Input(UUID objectId, FileRequest declaration) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record InputIssue(String fileName, String code, String clientFileId) {}

    record Failure(String code, Stage stage, boolean retryable) {}

    record Attribution(
            String title, String creator, String sourceURL, String licenseName, String licenseURL, String changes) {}

    record Upload(String clientFileId, String method, String url, Map<String, String> headers, String expiresAt) {}

    record Counts(long pending, long running, long ready, long noText, long failed, long cancelled) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record JobView(UUID jobId, SourceKind sourceKind, String sourceLanguage, String targetLanguage,
            JobStatus status, long revision, String expiresAt, List<PageSummary> pages, Counts counts,
            List<Upload> uploads, List<InputIssue> inputIssues, Failure failure, Attribution attribution) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PageSummary(UUID pageId, int order, PageState state, String fileName, Failure failure) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PageView(UUID pageId, int order, PageState state, String fileName, Failure failure, String originalURL,
            String assetExpiresAt, Integer widthPx, Integer heightPx, Integer ocrContractVersion,
            List<Segment> segments) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Segment(String segmentId, int readingOrder, String sourceText, String translatedText,
            List<List<Double>> polygon, Double confidence, String writingMode, boolean needsReview) {}

    record EngineResult(PageState state, int widthPx, int heightPx, int ocrContractVersion, List<Segment> segments,
            String errorCode, boolean retryable) {}

    record ImageInfo(String contentType, int widthPx, int heightPx) {
        long pixels() {
            return (long) widthPx * heightPx;
        }
    }

    record PageClaim(UUID jobId, UUID pageId, UUID token, String sourceLanguage, String targetLanguage, Page page) {}

    record PrepareClaim(Job job, UUID token) {}

    record Prepared(List<Page> pages, List<InputIssue> issues, Attribution attribution) {}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static final class Page {
        UUID id;
        UUID objectId;
        String fileName;
        String sourcePath;
        String sha256;
        int order;
        PageState state = PageState.PENDING;
        ImageInfo image;
        EngineResult result;
        Failure failure;
        UUID lease;
        long leaseUntil;
        long nextRun;
        int attempt;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static final class Job {
        UUID id;
        long owner;
        SourceKind sourceKind;
        String sourceLanguage;
        String targetLanguage;
        String sourceLink;
        JobStatus status;
        long revision = 1;
        long expiresAt;
        long uploadExpiresAt;
        long cleanupDue = Long.MAX_VALUE;
        long tombstoneUntil;
        UUID prepareLease;
        long prepareLeaseUntil;
        int prepareAttempts;
        List<Input> inputs = List.of();
        List<Page> pages = new ArrayList<>();
        List<InputIssue> inputIssues = List.of();
        Failure failure;
        Attribution attribution;
    }

    static final class RequestFailure extends RuntimeException {
        final HttpStatus status;
        final String code;

        // HTTP 응답에 안전한 고정 오류 코드만 보존한다.
        RequestFailure(HttpStatus status, String code) {
            super(code);
            this.status = status;
            this.code = code;
        }
    }

    static final class WorkFailure extends RuntimeException {
        final Failure failure;

        // 외부 응답 본문 대신 작업에 기록할 오류 정보만 보존한다.
        WorkFailure(String code, Stage stage, boolean retryable) {
            super(code);
            this.failure = new Failure(code, stage, retryable);
        }
    }
}
