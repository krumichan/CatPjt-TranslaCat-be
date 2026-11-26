package jp.co.translacat.domain.imagetranslation;

import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import software.amazon.awssdk.core.exception.SdkException;

import java.io.IOException;
import java.util.UUID;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.*;

/** 이미지 번역의 회차 API와 한정된 원본 전송을 제공한다. */
@RestController
@RequestMapping("/api/v1/image-translation/jobs")
@ConditionalOnProperty(prefix = "translacat.image-translation", name = "enabled", havingValue = "true")
public class ImageTranslationController {
    private static final String NO_STORE = "private, no-store";
    private static final String REQUEST_KEY = "Idempotency-Key";

    private final ImageTranslationJobs jobs;

    ImageTranslationController(ImageTranslationJobs jobs) {
        this.jobs = jobs;
    }

    /** 로그인 계정에 한해 입력 준비 작업을 생성하고 파일 전송 티켓을 발급한다. */
    @PostMapping
    public ResponseEntity<ResponseDto<JobView>> create(@AuthenticationPrincipal UserPrincipal user,
            @RequestHeader(REQUEST_KEY) String key, @RequestBody CreateRequest request) {
        return response(HttpStatus.CREATED, jobs.create(owner(user), key, request));
    }

    /** 로그인 계정의 회차 상태와 순서 고정 목록을 조회한다. */
    @GetMapping("/{jobId}")
    public ResponseEntity<ResponseDto<JobView>> get(
            @AuthenticationPrincipal UserPrincipal user, @PathVariable UUID jobId) {
        return response(HttpStatus.OK, jobs.get(owner(user), jobId));
    }

    /** 업로드를 확인하고 페이지 후보 준비를 비동기로 접수한다. */
    @PostMapping("/{jobId}/prepare")
    public ResponseEntity<ResponseDto<JobView>> prepare(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID jobId, @RequestHeader(REQUEST_KEY) String key) {
        return response(HttpStatus.ACCEPTED, jobs.prepare(owner(user), jobId, key));
    }

    /** 소유자가 고른 순서와 준비 상태 버전을 사용해 회차를 접수한다. */
    @PostMapping("/{jobId}/start")
    public ResponseEntity<ResponseDto<JobView>> start(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID jobId, @RequestHeader(REQUEST_KEY) String key, @RequestBody Selection selection) {
        return response(HttpStatus.ACCEPTED, jobs.start(owner(user), jobId, key, selection));
    }

    /** 소유자에게 요청한 페이지의 현재 상태와 원본·번역 결과를 제공한다. */
    @GetMapping("/{jobId}/pages/{pageId}")
    public ResponseEntity<ResponseDto<PageView>> page(
            @AuthenticationPrincipal UserPrincipal user, @PathVariable UUID jobId, @PathVariable UUID pageId) {
        return response(HttpStatus.OK, jobs.pageView(owner(user), jobId, pageId));
    }

    /** 이미 확정된 결과를 보존하면서 남은 회차 처리를 취소한다. */
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<ResponseDto<JobView>> cancel(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID jobId, @RequestHeader(REQUEST_KEY) String key) {
        return response(HttpStatus.ACCEPTED, jobs.cancel(owner(user), jobId, key));
    }

    /** 요청한 페이지 중 재시도 가능한 실패 항목만 다시 접수한다. */
    @PostMapping("/{jobId}/retry")
    public ResponseEntity<ResponseDto<JobView>> retry(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID jobId, @RequestHeader(REQUEST_KEY) String key, @RequestBody Selection selection) {
        return response(HttpStatus.ACCEPTED, jobs.retry(owner(user), jobId, key, selection));
    }

    /** 한정 URL에 지정된 원본을 스트리밍하고 완전히 전송된 파일만 저장한다. */
    @PutMapping("/{jobId}/files/{objectId}/upload")
    public ResponseEntity<Void> upload(@PathVariable UUID jobId, @PathVariable UUID objectId,
            @RequestParam long expires, @RequestParam String ticket, HttpServletRequest request) throws IOException {
        jobs.upload(jobId, objectId, expires, ticket, request.getContentType(), request.getInputStream());
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, NO_STORE).build();
    }

    /** 유효한 읽기 티켓에서만 검증한 이미지 미디어 타입으로 원본을 제공한다. */
    @GetMapping("/{jobId}/files/{objectId}/original")
    public ResponseEntity<InputStreamResource> original(@PathVariable UUID jobId, @PathVariable UUID objectId,
            @RequestParam long expires, @RequestParam String ticket) throws IOException {
        var asset = jobs.original(jobId, objectId, expires, ticket);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .body(new InputStreamResource(asset.stream()));
    }

    /** 작업 오류만 공통 비노출 오류 형식으로 바꾼다. */
    @ExceptionHandler(RequestFailure.class)
    public ResponseEntity<ResponseDto<Object>> failure(RequestFailure error) {
        return error(error.status, error.code);
    }

    /** 이 API에 전달한 ID, 요청 본문 또는 필수 헤더가 올바르지 않을 때 응답한다. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestValueException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ResponseDto<Object>> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    /** 저장소 연결 문제의 세부값을 노출하지 않고 재시도 가능 상태를 반환한다. */
    @ExceptionHandler({IOException.class, SdkException.class})
    public ResponseEntity<ResponseDto<Object>> unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "TEMPORARILY_UNAVAILABLE");
    }

    // 다른 인증 타입이 실수로 연결되더라도 계정 ID 없는 요청을 거절한다.
    private static long owner(UserPrincipal user) {
        if (user == null || user.getId() == null) {
            throw new RequestFailure(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
        return user.getId();
    }

    // 모든 회차 스냅샷은 사적 데이터이므로 공유 캐시를 막는다.
    private static <T> ResponseEntity<ResponseDto<T>> response(HttpStatus status, T body) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .body(ResponseUtil.ok(status, body));
    }

    // 현재 BE 표준 메시지·오류 코드를 유지하고 trace는 비운다.
    private static ResponseEntity<ResponseDto<Object>> error(HttpStatus status, String code) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .body(ResponseUtil.error(status.value(), status.getReasonPhrase(), code, null, false));
    }
}
