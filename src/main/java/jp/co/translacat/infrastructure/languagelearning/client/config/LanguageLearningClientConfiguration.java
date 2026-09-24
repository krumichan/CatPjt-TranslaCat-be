package jp.co.translacat.infrastructure.languagelearning.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningKeywordClient;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningSettingsClient;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(LanguageLearningClientProperties.class)
public class LanguageLearningClientConfiguration {

    static void validate(LanguageLearningClientProperties properties) {
        requireText(properties.getUrl(), "language-learning.url");
        URI uri;
        try {
            uri = URI.create(properties.getUrl());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Language Learning URL 형식이 올바르지 않습니다.");
        }
        if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalStateException("Language Learning URL은 자격증명/쿼리/경로가 없는 http(s) 원점이어야 합니다.");
        }
        if ("http".equals(uri.getScheme()) && !java.util.Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost())) {
            throw new IllegalStateException("loopback 이외의 Language Learning URL에는 HTTPS가 필요합니다.");
        }

        LanguageLearningClientProperties.Remote remote =
                properties.getRemote();
        if (remote.getConnectTimeoutMs() <= 0
                || remote.getReadTimeoutMs() <= 0) {
            throw new IllegalStateException(
                    "Language Learning HTTP timeout은 0보다 커야 합니다."
            );
        }
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 설정이 필요합니다.");
        }
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "language-learning.remote",
            name = "enabled",
            havingValue = "true"
    )
    public LanguageLearningInternalJwtProvider languageLearningInternalJwtProvider(
            LanguageLearningClientProperties properties
    ) {
        return new LanguageLearningInternalJwtProvider(
                properties,
                Clock.systemUTC()
        );
    }

    @Bean("languageLearningRestClient")
    @ConditionalOnProperty(
            prefix = "language-learning.remote",
            name = "enabled",
            havingValue = "true"
    )
    public RestClient languageLearningRestClient(
            RestClient.Builder builder,
            LanguageLearningClientProperties properties
    ) {
        validate(properties);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRemote().getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getRemote().getReadTimeoutMs()));

        return builder
                .baseUrl(trimTrailingSlash(properties.getUrl()))
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "language-learning.remote",
            name = "enabled",
            havingValue = "true"
    )
    public LanguageLearningSettingsClient languageLearningSettingsClient(
            @Qualifier("languageLearningRestClient")
            RestClient languageLearningRestClient,
            LanguageLearningInternalJwtProvider jwtProvider,
            ObjectMapper objectMapper
    ) {
        return new LanguageLearningSettingsClient(
                languageLearningRestClient,
                jwtProvider,
                objectMapper
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "language-learning.remote", name = "enabled", havingValue = "true")
    public LanguageLearningKeywordClient languageLearningKeywordClient(
            @Qualifier("languageLearningRestClient") RestClient restClient,
            LanguageLearningInternalJwtProvider jwtProvider,
            ObjectMapper objectMapper
    ) {
        return new LanguageLearningKeywordClient(restClient, jwtProvider, objectMapper);
    }
}
