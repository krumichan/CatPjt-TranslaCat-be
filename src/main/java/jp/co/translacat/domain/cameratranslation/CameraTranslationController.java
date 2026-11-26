package jp.co.translacat.domain.cameratranslation;

import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.Accepted;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.JobView;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.TranslationRequest;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.TranslationResult;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

import static org.springframework.http.HttpStatus.ACCEPTED;

/** 인증된 모바일 클라이언트에 카메라의 텍스트 번역과 추가 OCR을 제공한다. */
@RestController
@RequestMapping(value = "/api/v1/image-translation", produces = MediaType.APPLICATION_JSON_VALUE)
public class CameraTranslationController {
    private final CameraTextTranslation translation;
    private final CameraOcrJobs jobs;

    /** 카메라가 소유하는 공개 경로만 등록한다. */
    CameraTranslationController(CameraTextTranslation translation, CameraOcrJobs jobs) {
        this.translation = translation;
        this.jobs = jobs;
    }

    /**
     * 표현 목록의 번역 또는 표현별 실패를 반환한다.
     *
     * @param principal 현재 인증한 계정
     * @param request 현재 선택에서 읽은 텍스트와 언어 방향
     * @return 표현 ID가 보존된 번역 응답
     */
    @PostMapping(value = "/translations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDto<TranslationResult>> translate(@AuthenticationPrincipal UserPrincipal principal,
            @RequestBody TranslationRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ResponseUtil.ok(translation.translate(principal.getId(), request)));
    }

    /**
     * 사용자가 선택한 정지 crop의 추가 OCR을 접수한다.
     *
     * @param principal 현재 인증한 계정
     * @param key 재접수를 방지하는 계정별 키
     * @param image 선택 영역에서 만든 JPEG 또는 PNG
     * @param imageId 모바일에서 현재 선택에 부여한 ID
     * @param language 인식할 원어 코드
     * @return 현재 작업과 상태 조회 위치
     */
    @PostMapping(value = "/ocr-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseDto<Accepted>> createOcrJob(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("Idempotency-Key") UUID key, @RequestPart("image") MultipartFile image,
            @RequestParam("imageId") UUID imageId, @RequestParam("sourceLanguage") String language) {
        CameraTranslationInput.validateOcrLanguage(language);
        Accepted accepted = jobs.enqueue(
                principal.getId(), key, imageId, language, CameraTranslationInput.validateCrop(image));
        return ResponseEntity.status(ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .location(URI.create("/api/v1/image-translation/ocr-jobs/" + accepted.jobId()))
                .body(ResponseUtil.ok(ACCEPTED, accepted));
    }

    /**
     * 현재 계정이 소유한 유효 작업의 상태를 조회한다.
     *
     * @param principal 현재 인증한 계정
     * @param jobId 조회할 OCR 작업 ID
     * @return 작업 상태와 가능한 결과 또는 실패 코드
     */
    @GetMapping("/ocr-jobs/{jobId}")
    public ResponseEntity<ResponseDto<JobView>> getOcrJob(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID jobId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ResponseUtil.ok(jobs.get(principal.getId(), jobId)));
    }

    /**
     * 현재 계정의 작업을 취소하고 입력과 결과를 제거한다.
     *
     * @param principal 현재 인증한 계정
     * @param jobId 취소할 OCR 작업 ID
     * @return 취소가 반영된 빈 204 응답
     */
    @DeleteMapping("/ocr-jobs/{jobId}")
    public ResponseEntity<Void> cancelOcrJob(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID jobId) {
        jobs.cancel(principal.getId(), jobId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
