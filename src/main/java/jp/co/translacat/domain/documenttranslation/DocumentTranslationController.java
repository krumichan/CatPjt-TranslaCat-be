package jp.co.translacat.domain.documenttranslation;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.global.security.UserPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** JWT 사용자에게 문서 작업과 비공개 결과의 HTTP 진입점만 제공한다. */
@RestController
@RequestMapping(DocumentTranslationController.BASE)
@ConditionalOnProperty(name = "translacat.document-translation.enabled", havingValue = "true")
public class DocumentTranslationController {
    static final String BASE = "/api/v1/document-translations";
    private static final long MAX_REQUEST_BYTES = 12L * 1024 * 1024;

    private final DocumentTranslationService service;
    private final Clock clock;

    // 공용 인증 객체는 이 진입점에서만 사용한다.
    DocumentTranslationController(DocumentTranslationService service) {
        this.service = service;
        this.clock = Clock.systemUTC();
    }

    /** 파일 한 개의 접수를 확인하고 소유자의 작업 상태를 반환한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobResponse> submit(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(name = "file", required = false) List<MultipartFile> files,
            @RequestParam(name = "sourceLanguage", required = false) String source,
            @RequestParam(name = "targetLanguage", required = false) String target, HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            throw new DocumentTranslationException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        if (files == null || files.size() != 1 || target == null
                || request.getParameterValues("targetLanguage").length != 1
                || source != null && request.getParameterValues("sourceLanguage").length != 1) {
            throw new DocumentTranslationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }
        return accepted(service.submit(principal.getId(), uuid(idempotencyKey), files.getFirst(), source, target));
    }

    /** 인증한 계정이 소유한 단일 작업의 현재 상태를 반환한다. */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> get(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String jobId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(view(service.get(principal.getId(), uuid(jobId))));
    }

    /** 진행 작업에 취소를 요청하고 확정 상태에 따라 200 또는 202를 반환한다. */
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<JobResponse> cancel(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String jobId) {
        JobResponse response = view(service.cancel(principal.getId(), uuid(jobId)));
        var status = response.status().equals("CANCELLED") ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(response);
    }

    /** 보관 중인 원본을 이용해 새로운 문서 작업을 접수한다. */
    @PostMapping("/{jobId}/retry")
    public ResponseEntity<JobResponse> retry(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String jobId, @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return accepted(service.retry(principal.getId(), uuid(jobId), uuid(key)));
    }

    /** 유효한 성공 작업의 같은 형식 파일 전체를 스트리밍한다. */
    @GetMapping("/{jobId}/download")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String jobId) {
        var file = service.download(principal.getId(), uuid(jobId));
        String mime = switch (file.format()) {
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> throw new IllegalStateException("Unknown stored document format");
        };
        var name = ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).contentLength(file.bytes())
                .header("Content-Disposition", name.toString()).header("Cache-Control", "private, no-store")
                .body(new InputStreamResource(file.content()));
    }

    /** 문서 기능이 공개할 실패 코드만 응답으로 변환한다. */
    @ExceptionHandler(DocumentTranslationException.class)
    public ResponseEntity<ErrorResponse> error(DocumentTranslationException failure) {
        return ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse(failure.code(), failure.code()));
    }

    /** 서블릿의 파일 크기 초과를 문서 업로드 형식으로 반환한다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> fileTooLarge() {
        return error(new DocumentTranslationException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE"));
    }

    /** JDBC 장애를 파일과 SQL 정보가 없는 일시 오류로 반환한다. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> persistenceUnavailable() {
        return error(new DocumentTranslationException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE"));
    }

    // 접수 응답은 작업 URL과 같은 JSON 상태를 제공한다.
    private ResponseEntity<JobResponse> accepted(DocumentJob job) {
        return ResponseEntity.accepted().location(URI.create(BASE + "/" + job.id()))
                .cacheControl(CacheControl.noStore()).body(view(job));
    }

    // 파일 수명이 끝나면 청소 작업이 늦어져도 다운로드 경로를 숨긴다.
    private JobResponse view(DocumentJob job) {
        boolean expired = !clock.instant().isBefore(job.expiresAt());
        DocumentJob.State state = expired ? DocumentJob.State.EXPIRED : job.state();
        String downloadPath = state == DocumentJob.State.SUCCEEDED ? BASE + "/" + job.id() + "/download" : null;
        return new JobResponse(job.id(), state.name(), job.format(), job.fileName(), job.sourceLanguage(),
                job.targetLanguage(), job.completedSegments(), job.totalSegments(), job.warnings(),
                state == DocumentJob.State.FAILED ? job.errorCode() : null,
                job.expiresAt().isAfter(clock.instant().plusSeconds(180)) && state.permitsRetry(job.errorCode()),
                downloadPath, state == DocumentJob.State.SUCCEEDED ? job.outputBytes() : null,
                job.createdAt(), job.expiresAt());
    }

    // 표준 UUID의 축약 표기나 누락값을 성공으로 해석하지 않는다.
    private static UUID uuid(String raw) {
        try {
            if (raw != null) {
                UUID parsed = UUID.fromString(raw);
                if (parsed.toString().equalsIgnoreCase(raw)) {
                    return parsed;
                }
            }
        } catch (IllegalArgumentException malformed) {
            // 모든 UUID 오류는 같은 공개 응답을 사용한다.
        }
        throw new DocumentTranslationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    /** 신규 접수와 조회에서 공통으로 사용하는 작업 상태다. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record JobResponse(UUID jobId, String status, String format, String fileName, String sourceLanguage,
            String targetLanguage, int completedSegments, Integer totalSegments, List<DocumentJob.Warning> warnings,
            String errorCode, boolean retryable, String downloadPath, Long outputSizeBytes,
            Instant createdAt, Instant expiresAt) {}

    /** 서버·공급자의 원문을 포함하지 않는 문서 오류다. */
    public record ErrorResponse(String code, String message) {}
}
