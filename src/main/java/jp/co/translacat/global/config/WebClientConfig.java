package jp.co.translacat.global.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClientConfig 클래스
 *
 * Spring WebFlux의 WebClient를 설정하는 구성 클래스입니다.
 * WebClient는 비동기/논블로킹 HTTP 요청을 보낼 때 사용됩니다.
 */
@Configuration
public class WebClientConfig {

    @Value("${web-client.timeout.response.seconds:60}")
    private Long responseTimeoutSeconds;

    @Value("${web-client.timeout.connect.milli:10000}")
    private Integer connectTimeoutMilli;

    /**
     * WebClient Bean 생성
     *
     * WebClient.Builder를 이용하여 WebClient 인스턴스를 생성합니다.
     * 아래 옵션을 설정합니다:
     * 1. Reactor Netty HttpClient 사용
     * 2. 응답 대기 시간(response timeout) 5초
     * 3. 연결 시간(connect timeout) 3초
     * 4. 기본 Content-Type 헤더를 application/json으로 설정
     *
     * @param builder Spring에서 주입되는 WebClient.Builder
     * @return 구성된 WebClient Bean
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMilli)
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(responseTimeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(responseTimeoutSeconds, TimeUnit.SECONDS)));

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
