package jp.co.translacat.infrastructure.client.legacy;

import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Downstream 4xx response.
 *
 * <p>4xx responses represent request/business-contract failures rather than downstream
 * availability failures, so circuit breakers may explicitly ignore this exception.</p>
 */
public class ExternalApiClient4xxException extends RuntimeException {

    private final WebClientResponseException responseException;

    public ExternalApiClient4xxException(WebClientResponseException responseException) {
        super(responseException.getMessage(), responseException);
        this.responseException = responseException;
    }

    public WebClientResponseException getResponseException() {
        return responseException;
    }
}
