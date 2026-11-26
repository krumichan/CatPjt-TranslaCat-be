package jp.co.translacat.domain.cameratranslation;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.TranslationRequest;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.TranslationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/** 카메라의 내부 AI 호출 계약을 기존 서버 인증과 HTTP 전송에 연결한다. */
@Component
class CameraTranslationAi {
    private final WebClient client;
    private final String baseUrl;
    private final String apiKey;

    /** 기존 AI 서버 설정과 WebClient만 공유한다. */
    CameraTranslationAi(WebClient client, @Value("${ai-server.url}") String aiUrl,
            @Value("${ai-server.api-key}") String apiKey) {
        this.client = client;
        this.baseUrl = aiUrl.replaceFirst("/+$", "") + "/api/v1/camera-translation";
        this.apiKey = apiKey;
    }

    /** 현재 표현들을 한 번만 번역하며 예산 초과는 호출자에게 전달한다. */
    TranslationResult translate(TranslationRequest request, Duration timeout) {
        try {
            return client.post()
                    .uri(baseUrl + "/segments")
                    .header("X-API-KEY", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TranslationResult.class)
                    .timeout(timeout)
                    .block();
        } catch (RuntimeException failure) {
            throw new UpstreamFailure(failureKind(failure));
        }
    }

    /** 비공개 crop 한 장을 보내고 공통 OCR 결과를 받는다. */
    JsonNode recognize(UUID imageId, String language, CameraTranslationInput.Crop crop, Duration timeout) {
        // 사용자 파일명은 전송하지 않으며 고정된 파일 부분에 이미지 한 개만 담는다.
        var multipart = new MultipartBodyBuilder();
        String filename = "image/png".equals(crop.contentType()) ? "crop.png" : "crop.jpg";
        multipart.part("image", new ByteArrayResource(crop.bytes()))
                .filename(filename)
                .contentType(MediaType.parseMediaType(crop.contentType()));
        multipart.part("imageId", imageId.toString());
        multipart.part("sourceLanguage", language);

        try {
            return client.post()
                    .uri(baseUrl + "/ocr")
                    .header("X-API-KEY", apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(multipart.build()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(timeout)
                    .block();
        } catch (RuntimeException failure) {
            throw new UpstreamFailure(failureKind(failure));
        }
    }

    // 고정된 범주만 남겨 AI 본문과 전송 오류 메시지의 외부 노출을 막는다.
    private static FailureKind failureKind(RuntimeException failure) {
        if (failure instanceof WebClientResponseException response) {
            int code = response.getStatusCode().value();
            return switch (code) {
                case 400, 413, 415, 422 -> FailureKind.INVALID;
                case 504 -> FailureKind.TIMEOUT;
                default -> FailureKind.UNAVAILABLE;
            };
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof io.netty.handler.timeout.TimeoutException) {
                return FailureKind.TIMEOUT;
            }
        }
        return FailureKind.UNAVAILABLE;
    }

    enum FailureKind {
        INVALID, TIMEOUT, UNAVAILABLE
    }

    static final class UpstreamFailure extends RuntimeException {
        private final FailureKind kind;

        /** 민감한 전송 원인 대신 호출자가 처리할 실패 범주를 보존한다. */
        UpstreamFailure(FailureKind kind) {
            super(kind.name());
            this.kind = kind;
        }

        FailureKind kind() {
            return kind;
        }
    }
}
