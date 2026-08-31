package jp.co.translacat.infrastructure.client.legacy;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jp.co.translacat.global.exception.ExternalApiInvocationException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExternalApiClient {

    private static final int MAX_BINARY_RESPONSE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ERROR_RESPONSE_BODY_CHARS = 1000;
    private static final String REDACTED_VALUE = "[REDACTED]";
    private static final Set<String> SENSITIVE_HEADER_NAMES = Set.of(
            "authorization",
            "proxy-authorization",
            "x-api-key",
            "api-key",
            "apikey",
            "cookie",
            "set-cookie"
    );

    private final WebClient webClient;

    /**
     * 외부 API GET 요청을 수행한다.
     * - 요청 실패 시 Retry를 수행한다.
     * - Retry 후에도 실패하면 CircuitBreaker가 감지하여 fallback 메서드를 호출한다.
     *
     * @param uri 요청할 API 경로
     * @param responseType 응답을 매핑할 클래스 타입
     * @return API 응답을 매핑한 객체
     */
    @CircuitBreaker(name = "externalApiClient", fallbackMethod = "getFallback")
    @Retry(name = "externalApiClient")
    public <T> T get(String uri, Class<T> responseType) {
        return webClient.get()
                .uri(uri)
                .retrieve() // HTTP 응답을 수신하여 에러 상태 (4xx, 5xx)를 확인하고 적절한 예외를 발생시킨다.
                .bodyToMono(responseType) // response body를 지정 타입(responseType)으로 역직렬화하여 Mono<T> 형태로 변환
                .block(); // Mono<T> 형태의 값을 T 값으로 동기 방식으로 변환
    }

    /**
     * 외부 API POST 요청을 수행한다.
     * - 요청 실패 시 Retry를 수행한다.
     * - Retry 후에도 실패하면 CircuitBreaker가 fallback 메서드를 호출한다.
     *
     * @param uri 요청할 API 경로
     * @param body POST 요청 바디
     * @param responseType 응답을 매핑할 클래스 타입
     * @return API 응답을 매핑한 객체
     */
    @Retry(name = "externalApiClient")
    @CircuitBreaker(name = "externalApiClient", fallbackMethod = "postFallback")
    public <T, R> R post(String uri, T body, Class<R> responseType) {
        return webClient.post()
                .uri(uri)
                .bodyValue(body)
                .retrieve() // HTTP 응답을 수신하여 에러 상태 (4xx, 5xx)를 확인하고 적절한 예외를 발생시킨다.
                .bodyToMono(responseType) // response body를 지정 타입(responseType)으로 역직렬화하여 Mono<T> 형태로 변환
                .block(); // Mono<T> 형태의 값을 T 값으로 동기 방식으로 변환
    }

    @Retry(name = "externalApiClient")
    @CircuitBreaker(name = "externalApiClient", fallbackMethod = "postFallback")
    public <T, R> R post(String uri, T body, Map<String, String> headers, Class<R> responseType) {
        return webClient.post()
                .uri(uri)
                .headers(h -> headers.forEach(h::add))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(responseType)
                .block();
    }

    @Retry(name = "externalApiClient")
    @CircuitBreaker(name = "externalApiClient", fallbackMethod = "postMultipartFallback")
    public <R> R postMultipart(String uri, MultiValueMap<String, HttpEntity<?>> multipartData, Map<String, String> headers, Class<R> responseType) {
        return webClient.post()
            .uri(uri)
            .headers(h -> headers.forEach(h::add))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipartData))
            .retrieve()
            .bodyToMono(responseType)
            .block();
    }

    /**
     * GET 요청 실패 시 호출되는 fallback 메서드.
     * - Retry 및 CircuitBreaker를 모두 거친 후 최종적으로 실패했을 때 실행된다.
     *
     * @param uri 원본 요청 URI
     * @param responseType 매핑 대상 클래스 타입
     * @param throwable 발생한 예외 정보
     * @return throw exception
     */
    public <T> T  getFallback(String uri, Class<T> responseType, Throwable throwable) {
        String errorMessage = String.format("[External API Error] URI: %s | Cause: %s", uri, getErrorMessage(throwable));
        throw new ExternalApiInvocationException(errorMessage, throwable);
    }

    /**
     * POST 요청 실패 시 호출되는 fallback 메서드.
     * - Retry 및 CircuitBreaker를 모두 거친 후 최종적으로 실패했을 때 실행된다.
     *
     * @param uri 원본 요청 URI
     * @param body POST 요청 바디
     * @param responseType 매핑 대상 클래스 타입
     * @param throwable 발생한 예외 정보
     * @return throw exception
     */
    public <T, R> R postFallback(String uri, T body, Class<R> responseType, Throwable throwable) {
        String errorMessage = String.format(
                "[External API POST Error] URI: %s | Body: %s | Cause: %s",
                uri, summarizeBody(body), getErrorMessage(throwable)
        );
        throw new ExternalApiInvocationException(errorMessage, throwable);
    }

    /**
     * POST 요청 실패 시 호출되는 fallback 메서드.
     * - Retry 및 CircuitBreaker를 모두 거친 후 최종적으로 실패했을 때 실행된다.
     *
     * @param uri 원본 요청 URI
     * @param body POST 요청 바디
     * @param responseType 매핑 대상 클래스 타입
     * @param throwable 발생한 예외 정보
     * @return throw exception
     */
    public <T, R> R postFallback(
            String uri,
            T body,
            Map<String, String> headers,
            Class<R> responseType,
            Throwable throwable
    ) {
        String errorMessage = String.format(
                "[External API POST Error] URI: %s | Headers: %s | Body: %s | Cause: %s",
                uri, sanitizeHeaders(headers), summarizeBody(body), getErrorMessage(throwable)
        );
        throw new ExternalApiInvocationException(errorMessage, throwable);
    }

    public <R> R postMultipartFallback(
            String uri,
            MultiValueMap<String, HttpEntity<?>> multipartData,
            Map<String, String> headers,
            Class<R> responseType,
            Throwable throwable
    ) {
        String errorMessage = String.format(
                "[External API Multipart Error] URI: %s | Headers: %s | Body: %s | Cause: %s",
                uri,
                sanitizeHeaders(headers),
                summarizeMultipartBody(multipartData),
                getErrorMessage(throwable)
        );
        throw new ExternalApiInvocationException(errorMessage, throwable);
    }

    /**
     * Retry를 적용하지 않는 단발 POST.
     * 자체 Retry 정책을 가진 downstream 서비스 호출에 사용한다.
     * CircuitBreaker는 유지하여 최종 실패에 대한 보호는 적용한다.
     */
    @CircuitBreaker(name = "externalApiClient", fallbackMethod = "postFallback")
    public <T, R> R postOnce(
            String uri,
            T body,
            Map<String, String> headers,
            Class<R> responseType
    ) {
        return executePostOnce(uri, body, headers, responseType);
    }

    /**
     * Level Test 문제 풀 배치 전용 단발 POST.
     * 사용자 요청과 별도 Circuit Breaker를 사용하여 배치 장애가 실시간 학습 요청으로 전파되지 않게 한다.
     */
    @CircuitBreaker(name = "levelTestPoolBatchAi", fallbackMethod = "postFallback")
    public <T, R> R postOnceLevelTestPool(
            String uri,
            T body,
            Map<String, String> headers,
            Class<R> responseType
    ) {
        return executePostOnce(uri, body, headers, responseType);
    }

    private <T, R> R executePostOnce(
            String uri,
            T body,
            Map<String, String> headers,
            Class<R> responseType
    ) {
        try {
            return webClient.post()
                    .uri(uri)
                    .headers(h -> headers.forEach(h::add))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new ExternalApiClient4xxException(e);
            }
            throw e;
        }
    }


    /**
     * Retry를 적용하지 않는 단발 Multipart POST.
     * 자체 단계별 Retry 정책을 가진 AI Speaking 호출에 사용한다.
     */
    @CircuitBreaker(
            name = "externalApiClient",
            fallbackMethod = "postMultipartFallback"
    )
    public <R> R postMultipartOnce(
            String uri,
            MultiValueMap<String, HttpEntity<?>> multipartData,
            Map<String, String> headers,
            Class<R> responseType
    ) {
        return webClient.post()
                .uri(uri)
                .headers(h -> headers.forEach(h::add))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartData))
                .retrieve()
                .bodyToMono(responseType)
                .block();
    }

    /**
     * Retry를 적용하지 않는 단발 Binary GET.
     */
    @CircuitBreaker(
            name = "externalApiClient",
            fallbackMethod = "getBytesFallback"
    )
    public byte[] getBytesOnce(
            String uri,
            Map<String, String> headers
    ) {
        return webClient.get()
                .uri(uri)
                .headers(h -> headers.forEach(h::add))
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return DataBufferUtils.join(
                                    response.bodyToFlux(DataBuffer.class),
                                    MAX_BINARY_RESPONSE_BYTES
                            )
                            .map(buffer -> {
                                try {
                                    byte[] bytes = new byte[buffer.readableByteCount()];
                                    buffer.read(bytes);
                                    return bytes;
                                } finally {
                                    DataBufferUtils.release(buffer);
                                }
                            })
                            .defaultIfEmpty(new byte[0]);
                })
                .block();
    }

    public byte[] getBytesFallback(
            String uri,
            Map<String, String> headers,
            Throwable throwable
    ) {
        String errorMessage = String.format(
                "[External API Binary GET Error] URI: %s | Cause: %s",
                uri,
                getErrorMessage(throwable)
        );
        throw new ExternalApiInvocationException(errorMessage, throwable);
    }

    private Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        headers.forEach((name, value) -> sanitized.put(
                name,
                isSensitiveHeader(name) ? REDACTED_VALUE : value
        ));
        return sanitized;
    }

    private boolean isSensitiveHeader(String name) {
        return name != null
                && SENSITIVE_HEADER_NAMES.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    private String summarizeBody(Object body) {
        if (body == null) {
            return "empty";
        }
        return body.getClass().getSimpleName();
    }

    private String summarizeMultipartBody(
            MultiValueMap<String, HttpEntity<?>> multipartData
    ) {
        if (multipartData == null || multipartData.isEmpty()) {
            return "empty multipart";
        }
        return "multipart(parts=" + multipartData.keySet() + ")";
    }

    private String getErrorMessage(Throwable t) {
        if (t instanceof ExternalApiClient4xxException clientError) {
            var e = clientError.getResponseException();
            return String.format(
                    "서버 응답 에러 (Status: %d, Body: %s)",
                    e.getRawStatusCode(),
                    summarizeErrorResponseBody(e.getResponseBodyAsString())
            );
        } else if (t instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            return "서킷 브레이커가 열려 있어 요청이 차단되었습니다 (Circuit Breaker Open)";
        } else if (t instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            var e = (org.springframework.web.reactive.function.client.WebClientResponseException) t;
            return String.format(
                    "서버 응답 에러 (Status: %d, Body: %s)",
                    e.getRawStatusCode(),
                    summarizeErrorResponseBody(e.getResponseBodyAsString())
            );
        } else if (t instanceof java.net.ConnectException || t instanceof java.util.concurrent.TimeoutException) {
            return "연결 실패 또는 타임아웃이 발생했습니다";
        }
        return t.getMessage();
    }

    private String summarizeErrorResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty";
        }

        String singleLine = responseBody.replace('\r', ' ').replace('\n', ' ').trim();
        if (singleLine.length() <= MAX_ERROR_RESPONSE_BODY_CHARS) {
            return singleLine;
        }
        return singleLine.substring(0, MAX_ERROR_RESPONSE_BODY_CHARS) + "...<truncated>";
    }
}
