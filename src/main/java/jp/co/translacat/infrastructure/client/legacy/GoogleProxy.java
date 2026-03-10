package jp.co.translacat.infrastructure.client.legacy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

@Slf4j
@Service
public class GoogleProxy {

    private final WebClient googleProxyWebClient;

    @Value("${external.google.proxy-url}")
    private String googleProxyUrl;

    public GoogleProxy(@Qualifier("googleProxyWebClient") WebClient webClient) {
        this.googleProxyWebClient = webClient;
    }

    public <T> T get(String uri, Class<T> responseType) {
        return doRequest(uri, responseType);
    }

    private <T> T doRequest(String uri, Class<T> responseType) {

        String finalUri = UriComponentsBuilder.fromUri(URI.create(googleProxyUrl))
                .queryParam("url", uri)
                .build()
                .toUriString();

        log.info("[GoogleProxy] Final Proxy URL: {}", finalUri);

        return googleProxyWebClient.get()
                .uri(finalUri)
                .exchangeToMono(response -> {
                    log.info("[GoogleProxy] Status Code: {}", response.statusCode());
                    // 헤더 정보 출력 (디버깅용)
                    response.headers().asHttpHeaders().forEach((k, v) -> log.debug("Header: {} = {}", k, v));
                    return response.bodyToMono(responseType);
                })
//                .retrieve()
//                .onStatus(
//                        status -> !status.is2xxSuccessful(),
//                        resp -> Mono.error(new RuntimeException("HTTP " + resp.statusCode()))
//                )
//                .bodyToMono(responseType)
                .switchIfEmpty(Mono.error(new RuntimeException("empty body")))
                .flatMap(r -> {
                    if (r instanceof String s && s.length() < 500) {
                        return Mono.error(new RuntimeException("small body"));
                    }
                    return Mono.just(r);
                })
                .retryWhen(reactor.util.retry.Retry.fixedDelay(5, Duration.ofSeconds(2)))
                .onErrorMap(e -> {
                    String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    return new RuntimeException(
                            "GoogleProxy failed after retry. url=" + finalUri + ", cause=" + cause,
                            e
                    );
                })
                .block();
    }
}
