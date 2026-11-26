package jp.co.translacat.domain.cameratranslation;

import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.Mode;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.SegmentResult;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.SegmentStatus;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.TranslationRequest;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.TranslationResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

/** 촬영과 미리보기 텍스트를 같은 무상태 번역 경로에서 처리한다. */
@Service
class CameraTextTranslation {
    private static final int MAX_CALLS_PER_WINDOW = 70;
    private static final int MAX_ACTIVE_WINDOWS = 10_000;
    private static final long WINDOW_MILLIS = 60_000L;
    private final CameraTranslationAi ai;
    private final Semaphore simultaneous = new Semaphore(8);
    private final LinkedHashMap<Long, Window> windows = new LinkedHashMap<>();

    /** 내부 번역 호출만 사용하고 OCR 작업 저장소와 독립적으로 처리한다. */
    CameraTextTranslation(CameraTranslationAi ai) {
        this.ai = ai;
    }

    /** 사용자의 표현 요청을 검증하고 같은 표현 ID 순서로 결과를 반환한다. */
    TranslationResult translate(long owner, TranslationRequest request) {
        CameraTranslationInput.validate(request);
        if (!simultaneous.tryAcquire()) {
            throw new CameraTranslationProblem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", 1);
        }

        // 인스턴스 내에서 동시성·계정 호출량을 제한하고 모델 요청을 한 번 보낸다.
        try {
            reserve(owner, System.currentTimeMillis());
            Duration timeout = request.mode() == Mode.PREVIEW ? Duration.ofSeconds(5) : Duration.ofSeconds(10);
            return normalize(request, ai.translate(request, timeout));
        } catch (CameraTranslationAi.UpstreamFailure failure) {
            if (failure.kind() == CameraTranslationAi.FailureKind.TIMEOUT) {
                throw new CameraTranslationProblem(HttpStatus.GATEWAY_TIMEOUT, "DEADLINE_EXCEEDED");
            }
            throw CameraTranslationProblem.unavailable();
        } finally {
            simultaneous.release();
        }
    }

    // 응답의 표현 ID로 대조하므로 모델 순서가 바뀌어도 화면에 뒤섞이지 않는다.
    private static TranslationResult normalize(TranslationRequest request, TranslationResult upstream) {
        var first = new HashMap<String, SegmentResult>();
        var duplicate = new HashSet<String>();
        if (upstream != null && request.requestId().equals(upstream.requestId()) && upstream.segments() != null) {
            for (SegmentResult segment : upstream.segments()) {
                if (segment != null && segment.id() != null && first.putIfAbsent(segment.id(), segment) != null) {
                    duplicate.add(segment.id());
                }
            }
        }

        List<SegmentResult> result = request.segments().stream().map(segment -> {
            String id = segment.id();
            if (duplicate.contains(id)) {
                return invalid(id);
            }
            var item = first.get(id);
            if (item != null && item.status() == SegmentStatus.TRANSLATED && item.translatedText() != null
                    && !item.translatedText().isBlank() && item.translatedText().length() <= 10_000
                    && item.errorCode() == null) {
                return item;
            }
            if (item != null && item.status() == SegmentStatus.FAILED && item.translatedText() == null
                    && "SEGMENT_TEMPORARILY_UNAVAILABLE".equals(item.errorCode())) {
                return item;
            }
            return invalid(id);
        }).toList();
        return new TranslationResult(request.requestId(), result);
    }

    // 외부 모델의 잘못된 결과는 표현 단위의 고정 오류로 대체한다.
    private static SegmentResult invalid(String id) {
        return new SegmentResult(id, SegmentStatus.FAILED, null, "INVALID_MODEL_OUTPUT");
    }

    // 만료한 선두 구간을 제거하고 현재 계정의 메모리 내 호출량을 예약한다.
    private synchronized void reserve(long owner, long now) {
        var iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().expiresAt() > now) {
                break;
            }
            iterator.remove();
        }

        Window prior = windows.get(owner);
        if (prior == null) {
            if (windows.size() >= MAX_ACTIVE_WINDOWS) {
                throw new CameraTranslationProblem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", 1);
            }
            windows.put(owner, new Window(now + WINDOW_MILLIS, 1));
            return;
        }
        if (prior.calls() >= MAX_CALLS_PER_WINDOW) {
            int seconds = Math.max(1, (int) ((prior.expiresAt() - now + 999) / 1_000));
            throw new CameraTranslationProblem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", seconds);
        }
        windows.put(owner, new Window(prior.expiresAt(), prior.calls() + 1));
    }

    private record Window(long expiresAt, int calls) {
    }
}
