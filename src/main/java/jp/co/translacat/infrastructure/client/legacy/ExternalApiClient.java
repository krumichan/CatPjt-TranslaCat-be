package jp.co.translacat.infrastructure.client.legacy;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jp.co.translacat.global.exception.ExternalApiInvocationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExternalApiClient {

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
        String bodyInfo = (body != null) ? body.toString() : "empty body";
        String errorMessage = String.format(
                "[External API POST Error] URI: %s | Body: %s | Cause: %s",
                uri, bodyInfo, getErrorMessage(throwable)
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
    public <T, R> R postFallback(String uri, T body, Map<String, String> headers, Class<R> responseType, Throwable throwable) {
        String bodyInfo = (body != null) ? body.toString() : "empty body";
        String errorMessage = String.format(
                "[External API POST Error] URI: %s | Headers: %s | Body: %s | Cause: %s",
                uri, bodyInfo, headers, getErrorMessage(throwable)
        );
        throw new ExternalApiInvocationException(errorMessage, throwable);
    }

    public <R> R postMultipartFallback(String uri, MultiValueMap<String, HttpEntity<?>> multipartData, Map<String, String> headers, Class<R> responseType, Throwable throwable) {
        String errorMessage = String.format(
                "[External API Multipart Error] URI: %s | Headers: %s | Body: %s | Cause: %s",
                uri, headers, multipartData, getErrorMessage(throwable)
        );
        throw new ExternalApiInvocationException(errorMessage, throwable);
    }

    private String getErrorMessage(Throwable t) {
        if (t instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            return "서킷 브레이커가 열려 있어 요청이 차단되었습니다 (Circuit Breaker Open)";
        } else if (t instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            var e = (org.springframework.web.reactive.function.client.WebClientResponseException) t;
            return String.format("서버 응답 에러 (Status: %d, Body: %s)", e.getRawStatusCode(), e.getResponseBodyAsString());
        } else if (t instanceof java.net.ConnectException || t instanceof java.util.concurrent.TimeoutException) {
            return "연결 실패 또는 타임아웃이 발생했습니다";
        }
        return t.getMessage();
    }
}
