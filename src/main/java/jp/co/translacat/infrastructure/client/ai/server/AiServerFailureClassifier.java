package jp.co.translacat.infrastructure.client.ai.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import jp.co.translacat.global.exception.AiServerFailureCode;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient4xxException;
import org.springframework.core.codec.DecodingException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;

final class AiServerFailureClassifier {
    private AiServerFailureClassifier() {}

    static AiServerFailureCode classify(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean webClientRequestFailure = false;
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof CallNotPermittedException) {
                return AiServerFailureCode.CIRCUIT_OPEN;
            }
            if (current instanceof ExternalApiClient4xxException) {
                return AiServerFailureCode.HTTP_4XX;
            }
            if (current instanceof WebClientResponseException response) {
                if (response.getStatusCode().is4xxClientError()) {
                    return AiServerFailureCode.HTTP_4XX;
                }
                if (response.getStatusCode().is5xxServerError()) {
                    return AiServerFailureCode.HTTP_5XX;
                }
            }
            if (current instanceof ConnectTimeoutException
                    || current instanceof ReadTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return AiServerFailureCode.CONNECT_TIMEOUT;
            }
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof HttpRetryException) {
                return AiServerFailureCode.CONNECT_FAILURE;
            }
            if (current instanceof WebClientRequestException) {
                webClientRequestFailure = true;
                continue;
            }
            if (current instanceof DecodingException || current instanceof JsonProcessingException) {
                return AiServerFailureCode.RESPONSE_DECODE;
            }
            if (current instanceof IllegalArgumentException) {
                return AiServerFailureCode.CONFIGURATION;
            }
        }
        return webClientRequestFailure
                ? AiServerFailureCode.CONNECT_FAILURE
                : AiServerFailureCode.UNKNOWN;
    }
}
