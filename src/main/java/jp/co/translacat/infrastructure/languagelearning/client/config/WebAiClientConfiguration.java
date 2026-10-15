package jp.co.translacat.infrastructure.languagelearning.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningWebAiClient;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class WebAiClientConfiguration {
    @Bean
    public LanguageLearningWebAiClient languageLearningWebAiClient(RestClient.Builder builder,
            LanguageLearningClientProperties properties, LanguageLearningInternalJwtProvider jwt, ObjectMapper json) {
        LanguageLearningClientConfiguration.validate(properties);
        int timeout = properties.getWebAi().getReadTimeoutMs();
        if (timeout < 240_000 || timeout > 600_000) {
            throw new IllegalStateException("Web AI 검증 HTTP timeout은 240000~600000ms여야 합니다.");
        }

        // 점수 검증 호출은 LL의 작업 기한을 포함하며 리다이렉트로 내부 JWT를 전달하지 않는다.
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRemote().getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(timeout));
        RestClient client = builder.clone().baseUrl(properties.getUrl()).requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).build();
        return new LanguageLearningWebAiClient(client, jwt, json);
    }
}
