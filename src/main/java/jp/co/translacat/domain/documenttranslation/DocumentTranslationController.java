package jp.co.translacat.domain.documenttranslation;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.co.translacat.global.security.UserPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** JWT 사용자에게 문서 작업과 비공개 결과의 HTTP 진입점만 제공한다. */
@RestController
@RequestMapping(DocumentTranslationController.BASE)
@ConditionalOnProperty(name = "translacat.document-translation.enabled", havingValue = "true")
public class DocumentTranslationController {
    static final String BASE = "/api/v1/document-translations";

    private final DocumentTranslationService service;
    private final Clock clock;
    private final Semaphore uploadTransfers;
    private final Semaphore downloadTransfers;

    // 공용 인증 객체는 이 진입점에서만 사용한다.
    DocumentTranslationController(DocumentTranslationService service, DocumentTranslationCapacity capacity) {
        this.service = service;
        this.clock = Clock.systemUTC();
        this.uploadTransfers = new Semaphore(capacity.uploads());
        this.downloadTransfers = new Semaphore(capacity.downloads());
    }

    /** Office 파일 하나를 요청 본문에서 읽어 소유자의 작업으로 접수한다. */
    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<JobResponse> submit(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(name = "format", required = false) String format,
            @RequestParam(name = "sourceLanguage", required = false) String source,
            @RequestParam(name = "targetLanguage", required = false) String target, HttpServletRequest request) {
        // 길이를 생략한 요청도 서비스의 스트리밍 한도를 적용한다.
        if (request.getContentLengthLong() > DocumentTranslationService.MAX_UPLOAD_BYTES) {
            throw new DocumentTranslationException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        if (format == null || target == null || request.getParameterValues("format").length != 1
                || request.getParameterValues("targetLanguage").length != 1
                || source != null && request.getParameterValues("sourceLanguage").length != 1) {
            throw new DocumentTranslationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }

        // 느린 업로드가 응답 전송용 메모리나 처리 작업 슬롯까지 점유하지 못하게 한다.
        if (!uploadTransfers.tryAcquire()) {
            throw new DocumentTranslationException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        }
        long deadline = System.nanoTime() + DocumentTranslationService.TRANSFER_NANOS;
        try (var input = request.getInputStream()) {
            return accepted(service.submit(principal.getId(), uuid(idempotencyKey), input, format, source, target,
                    deadline));
        } catch (IOException disconnected) {
            throw new DocumentTranslationException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        } finally {
            uploadTransfers.release();
        }
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

    /** 기존 재시도 URL에는 원본을 다시 올려야 한다는 상태를 반환한다. */
    @PostMapping("/{jobId}/retry")
    public ResponseEntity<JobResponse> retry(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String jobId, @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        uuid(key);
        service.get(principal.getId(), uuid(jobId));
        throw new DocumentTranslationException(HttpStatus.GONE, "FILE_EXPIRED");
    }

    /** 소유자의 첫 전체 GET에 현재 응답용 메모리 사본을 보내고 다시 열 수 없는 파일은 정리한다. */
    @GetMapping("/{jobId}/download")
    public void download(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String jobId,
            HttpServletRequest request, HttpServletResponse response) {
        // 사전 확인과 부분 요청이 일회 수령 기회를 먼저 사용하지 않도록 한다.
        if (!"GET".equals(request.getMethod())) {
            response.setHeader("Allow", "GET");
            throw new DocumentTranslationException(HttpStatus.METHOD_NOT_ALLOWED, "INVALID_REQUEST");
        }
        if (request.getHeader("Range") != null) {
            throw new DocumentTranslationException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "INVALID_REQUEST");
        }
        if (request.getHeader("If-Match") != null || request.getHeader("If-None-Match") != null
                || request.getHeader("If-Modified-Since") != null || request.getHeader("If-Unmodified-Since") != null) {
            throw new DocumentTranslationException(HttpStatus.PRECONDITION_FAILED, "INVALID_REQUEST");
        }

        // 파일 접근을 끝낸 뒤 보내므로 연결이 느려도 파일 사용권이 남지 않는다.
        if (!downloadTransfers.tryAcquire()) {
            throw new DocumentTranslationException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        }
        long deadline = System.nanoTime() + DocumentTranslationService.TRANSFER_NANOS;
        try {
            var file = service.download(principal.getId(), uuid(jobId));
            response.setContentType(downloadMime(file.format()));
            response.setContentLengthLong(file.content().length);
            var name = ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build();
            response.setHeader("Content-Disposition", name.toString());
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Accept-Ranges", "none");
            sendBody(response.getOutputStream(), file.content(), deadline);
            response.flushBuffer();
        } catch (IOException disconnected) {
            if (!response.isCommitted()) {
                response.reset();
                throw new DocumentTranslationException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
            }
        } finally {
            downloadTransfers.release();
        }
    }

    // 소켓의 읽기·쓰기 유휴 제한과 별도로 반복 전송의 전체 기한을 확인한다.
    private static void sendBody(OutputStream output, byte[] content, long deadline) throws IOException {
        int offset = 0;
        while (offset < content.length && System.nanoTime() - deadline < 0) {
            int length = Math.min(64 * 1024, content.length - offset);
            output.write(content, offset, length);
            offset += length;
        }
        if (System.nanoTime() - deadline >= 0) {
            throw new IOException("Document transfer deadline reached");
        }
    }

    /** 문서 기능이 공개할 실패 코드만 응답으로 변환한다. */
    @ExceptionHandler(DocumentTranslationException.class)
    public ResponseEntity<ErrorResponse> error(DocumentTranslationException failure) {
        return ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse(failure.code(), failure.code()));
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

    // 수령한 결과의 성공 메타데이터를 보존하고 수령 가능한 동안만 경로를 공개한다.
    private JobResponse view(DocumentJob job) {
        boolean ready = job.state() == DocumentJob.State.SUCCEEDED
                && DocumentTranslationService.resultMarker(job.id()).equals(job.outputKey());
        boolean expired = ready && !clock.instant().isBefore(job.expiresAt());
        DocumentJob.State state = expired ? DocumentJob.State.EXPIRED : job.state();
        String downloadPath = ready && !expired
                ? BASE + "/" + job.id() + "/download" : null;
        return new JobResponse(job.id(), state.name(), job.format(), "document." + job.format(), job.sourceLanguage(),
                job.targetLanguage(), job.completedSegments(), job.totalSegments(), job.warnings(),
                state == DocumentJob.State.FAILED ? job.errorCode() : null, false,
                downloadPath, state == DocumentJob.State.SUCCEEDED ? job.outputBytes() : null,
                job.createdAt(), job.expiresAt());
    }

    // 서버에서 검증한 Office 형식에 해당하는 다운로드 MIME을 선택한다.
    private static String downloadMime(String format) {
        return switch (format) {
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> throw new IllegalStateException("Unknown stored document format");
        };
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
